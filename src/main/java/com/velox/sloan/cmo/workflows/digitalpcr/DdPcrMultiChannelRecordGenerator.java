package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapio.commons.exemplar.plugin.PluginOrder;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* This plugin is making a duplicate record for existing DdPcrProtocol1SixChannels at step 10 of Digital Droplet PCR workflow
 * This makes the records expected as what QX600 software with 6 channels is expecting.
 * Expand team is using 2 channels at the moment.
 * @author mirhajf
* */
public class DdPcrMultiChannelRecordGenerator extends DefaultGenericPlugin {
    public DdPcrMultiChannelRecordGenerator() {
        setTaskEntry(true);
        setOrder(PluginOrder.MIDDLE.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return !activeTask.getTask().getTaskOptions().containsKey("DUPLICATE RECORDS CREATED") &&
                activeTask.getTask().getTaskOptions().containsKey("CREATE NEW SIX CHANNELS RECORDS");
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedDdpcrSixChannels = activeTask.getAttachedDataRecords("DdPcrProtocol1SixChannels", user);
            Map<String, Object> dataFieldValueMap = new HashMap<>();
            List<DataRecord> ddPcrSixChannelRecords = new ArrayList<>();

            String[] QXResultSheetType = {"Two Channels", "Six Channels"};
            int qxVesrsion = clientCallback.showOptionDialog("QX600 Type", "How many channels are you running?", QXResultSheetType, 0);
            logInfo("qx channels = " + qxVesrsion);
            boolean twoChannels = true;
            if (qxVesrsion == 1) {
                twoChannels = false;
            }
            int numOfChannels = 2;
            if (!twoChannels) {
                numOfChannels = 6;
            }
            this.activeTask.getTask().getTaskOptions().put("DUPLICATE RECORDS CREATED", "");
            logInfo("Added the duplicated task option to this step!");

            for (DataRecord sixChannelRec : attachedDdpcrSixChannels) {
                boolean firstTime = true;
                String target = "";
                String reference = "";
                String[] targetReference = new String[numOfChannels];
                for (int i = 1; i < numOfChannels; i++) {
                    dataFieldValueMap.put("Aliq1StartingVolume", sixChannelRec.getValue("Aliq1StartingVolume", user));
                    dataFieldValueMap.put("Aliq1StartingConcentration", sixChannelRec.getValue("Aliq1StartingConcentration", user));
                    dataFieldValueMap.put("Aliq1TargetMass", sixChannelRec.getValue("Aliq1TargetMass", user));
                    dataFieldValueMap.put("Aliq1TargetVolume", sixChannelRec.getValue("Aliq1TargetVolume", user));
                    dataFieldValueMap.put("Aliq1TargetConcentration", sixChannelRec.getValue("Aliq1TargetConcentration", user));
                    dataFieldValueMap.put("Aliq1SourceVolumeToUse", sixChannelRec.getValue("Aliq1SourceVolumeToUse", user));
                    dataFieldValueMap.put("Aliq1DilutantVolumeToUse", sixChannelRec.getValue("Aliq1DilutantVolumeToUse", user));
                    dataFieldValueMap.put("Aliq1TargetPlateId", sixChannelRec.getValue("Aliq1TargetPlateId", user));


                    dataFieldValueMap.put("Aliq1TargetWellPosition", sixChannelRec.getValue("Aliq1TargetWellPosition", user));
                    dataFieldValueMap.put("DropletReading", sixChannelRec.getValue("DropletReading", user));

                    dataFieldValueMap.put("Sampledescription3", sixChannelRec.getValue("Sampledescription3", user));
                    dataFieldValueMap.put("Sampledescription4", sixChannelRec.getValue("Sampledescription4", user));
                    sixChannelRec.setDataField("ExemplarSampleType", "Unknown", user);
                    dataFieldValueMap.put("AssayType", "Single Target per Channel");
                    sixChannelRec.setDataField("AssayType", "Single Target per Channel", user);
                    dataFieldValueMap.put("SupermixName", sixChannelRec.getValue("SupermixName", user));
                    dataFieldValueMap.put("AssayType", sixChannelRec.getValue("AssayType", user));
                    dataFieldValueMap.put("SignalCh1", "None");
                    dataFieldValueMap.put("Aliq1ControlType", sixChannelRec.getStringVal("Aliq1ControlType", user));
                    dataFieldValueMap.put("Aliq1IsNewControl", sixChannelRec.getBooleanVal("Aliq1IsNewControl", user));

                    //if (sixChannelRec.getBooleanVal("Aliq1IsNewControl", user) == Boolean.FALSE) {
                        if (firstTime) {
                            targetReference = sixChannelRec.getStringVal("TargetName", user).split(",");
                            logInfo("Target name is = " + sixChannelRec.getStringVal("TargetName", user));

                            if (targetReference.length < 2) {
                                clientCallback.displayError("Please include target and reference targets separated by comma; like: target, reference");
                            }
                            firstTime = false;
                        }
                        target = targetReference[0].trim();
                        reference = targetReference[i].trim();
                        sixChannelRec.setDataField("TargetName", target, user);
                        dataFieldValueMap.put("TargetName", reference);
                    //}
                    dataFieldValueMap.put("TargetType", "Unknown");
                    sixChannelRec.setDataField("SignalCh1", "FAM", user);

                    if (i == 1) {
                        dataFieldValueMap.put("SignalCh1", "None");
                        dataFieldValueMap.put("SignalCh2", "HEX");
                        dataFieldValueMap.put("SignalCh3", "None");
                        dataFieldValueMap.put("SignalCh4", "None");
                        dataFieldValueMap.put("SignalCh5", "None");
                        dataFieldValueMap.put("SignalCh6", "None");
                    }
                    logInfo("i is: " + i);
                    if (!twoChannels) {
                        if (i == 2) {
                            dataFieldValueMap.put("SignalCh2", "None");
                            dataFieldValueMap.put("SignalCh3", "Cy5");
                            dataFieldValueMap.put("SignalCh4", "None");
                            dataFieldValueMap.put("SignalCh5", "None");
                            dataFieldValueMap.put("SignalCh6", "None");
                        } else if (i == 3) {
                            dataFieldValueMap.put("SignalCh2", "None");
                            dataFieldValueMap.put("SignalCh3", "None");
                            dataFieldValueMap.put("SignalCh4", "Cy5.5");
                            dataFieldValueMap.put("SignalCh5", "None");
                            dataFieldValueMap.put("SignalCh6", "None");
                        } else if (i == 4) {
                            dataFieldValueMap.put("SignalCh2", "None");
                            dataFieldValueMap.put("SignalCh3", "None");
                            dataFieldValueMap.put("SignalCh4", "None");
                            dataFieldValueMap.put("SignalCh5", "ROX");
                            dataFieldValueMap.put("SignalCh6", "None");
                        }
                        else if (i == 5) {
                            dataFieldValueMap.put("SignalCh2", "None");
                            dataFieldValueMap.put("SignalCh3", "None");
                            dataFieldValueMap.put("SignalCh4", "None");
                            dataFieldValueMap.put("SignalCh5", "None");
                            dataFieldValueMap.put("SignalCh6", "ATTO 590");
                        }
                    } else { // two channels
                        dataFieldValueMap.put("SignalCh3", sixChannelRec.getValue("SignalCh3", user));
                        dataFieldValueMap.put("SignalCh4", sixChannelRec.getValue("SignalCh4", user));
                        dataFieldValueMap.put("SignalCh5", sixChannelRec.getValue("SignalCh5", user));
                        dataFieldValueMap.put("SignalCh6", sixChannelRec.getValue("SignalCh6", user));
                        dataFieldValueMap.put("WellNotes", sixChannelRec.getValue("WellNotes", user));
                    }
                    dataFieldValueMap.put("Plot", sixChannelRec.getValue("Plot", user));
                    dataFieldValueMap.put("RdqConversionFactor", sixChannelRec.getValue("RdqConversionFactor", user));


                    for (DataRecord sample : attachedSamples) {
                        if (sample.getBooleanVal("IsControl", user) == Boolean.TRUE &&
                                sixChannelRec.getStringVal("SampleId", user).equals(sample.getStringVal("SampleId", user))) {
                            dataFieldValueMap.put("SampleId", sample.getStringVal("SampleId", user));
                            dataFieldValueMap.put("OtherSampleId", sample.getStringVal("OtherSampleId", user));

                            sixChannelRec.setDataField("Sampledescription1", sample.getStringVal("SampleId", user), user);
                            sixChannelRec.setDataField("Sampledescription2", sample.getStringVal("OtherSampleId", user), user);
                            dataFieldValueMap.put("Sampledescription1", sample.getStringVal("SampleId", user));
                            dataFieldValueMap.put("Sampledescription2", sample.getStringVal("OtherSampleId", user));

                            ddPcrSixChannelRecords.add(sample.addChild("DdPcrProtocol1SixChannels", dataFieldValueMap, user));
                            break;
                        } else if (sample.getBooleanVal("IsControl", user) == Boolean.FALSE &&
                                sixChannelRec.getStringVal("SampleId", user).equals(sample.getParentsOfType("Sample", user).get(0).getStringVal("SampleId", user))) {
                            dataFieldValueMap.put("AltId", sample.getParentsOfType("Sample", user).get(0).getStringVal("AltId", user));
                            dataFieldValueMap.put("SampleId", sample.getParentsOfType("Sample", user).get(0).getStringVal("SampleId", user));
                            dataFieldValueMap.put("OtherSampleId", sample.getParentsOfType("Sample", user).get(0).getStringVal("OtherSampleId", user));

                            sixChannelRec.setDataField("Sampledescription1", sample.getParentsOfType("Sample", user).get(0).getStringVal("SampleId", user), user);
                            sixChannelRec.setDataField("Sampledescription2", sample.getParentsOfType("Sample", user).get(0).getStringVal("OtherSampleId", user), user);
                            dataFieldValueMap.put("Sampledescription1", sample.getParentsOfType("Sample", user).get(0).getStringVal("SampleId", user));
                            dataFieldValueMap.put("Sampledescription2", sample.getParentsOfType("Sample", user).get(0).getStringVal("OtherSampleId", user));
                            ddPcrSixChannelRecords.add(sample.addChild("DdPcrProtocol1SixChannels", dataFieldValueMap, user));
                            break;
                        }
                    }

                }
            }
            activeTask.addAttachedDataRecords(ddPcrSixChannelRecords);
        } catch (NotFound | IoError | InvalidValue e) {
            throw new RuntimeException(e);
        }
        return new PluginResult(true);
    }
}
