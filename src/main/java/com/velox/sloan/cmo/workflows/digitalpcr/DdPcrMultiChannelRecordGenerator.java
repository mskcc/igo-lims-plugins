package com.velox.sloan.cmo.workflows.digitalpcr;

import com.velox.api.datarecord.DataRecord;
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
 * @
* */
public class DdPcrMultiChannelRecordGenerator extends DefaultGenericPlugin {

    public DdPcrMultiChannelRecordGenerator() {
        setTaskEntry(true);
        setOrder(PluginOrder.FIRST.getOrder());
    }
    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey("CREATE NEW SIX CHANNELS RECORDS") &&
                !activeTask.getTask().getTaskOptions().containsKey("_DUPLICATE RECORDS CREATED");
    }

    @Override
    public PluginResult run() throws ServerException, RemoteException {
        try {
            List<DataRecord> attachedSamples = activeTask.getAttachedDataRecords("Sample", user);
            List<DataRecord> attachedDdpcrSixChannels = activeTask.getAttachedDataRecords("DdPcrProtocol1SixChannels", user);

            Map<String, Object> dataFieldValueMap = new HashMap<>();
            List<DataRecord> ddPcrSixChannelRecords = new ArrayList<>();

            for (DataRecord sixChannelRec : attachedDdpcrSixChannels) {
                dataFieldValueMap.put("Aliq1StartingVolume", sixChannelRec.getValue("Aliq1StartingVolume", user));
                dataFieldValueMap.put("Aliq1StartingConcentration", sixChannelRec.getValue("Aliq1StartingConcentration", user));
                dataFieldValueMap.put("Aliq1TargetMass", sixChannelRec.getValue("Aliq1TargetMass", user));
                dataFieldValueMap.put("Aliq1TargetVolume", sixChannelRec.getValue("Aliq1TargetVolume", user));
                dataFieldValueMap.put("Aliq1TargetConcentration", sixChannelRec.getValue("Aliq1TargetConcentration", user));
                dataFieldValueMap.put("Aliq1SourceVolumeToUse", sixChannelRec.getValue("Aliq1SourceVolumeToUse", user));
                dataFieldValueMap.put("Aliq1DilutantVolumeToUse", sixChannelRec.getValue("Aliq1DilutantVolumeToUse", user));

                dataFieldValueMap.put("AltId", sixChannelRec.getValue("AltId", user));
                dataFieldValueMap.put("SampleId", sixChannelRec.getValue("SampleId", user));
                logInfo("duplicate records plugin: sample ID = " + sixChannelRec.getValue("SampleId", user));
                dataFieldValueMap.put("OtherSampleId", sixChannelRec.getValue("OtherSampleId", user));

                dataFieldValueMap.put("Aliq1TargetWellPosition", sixChannelRec.getValue("Aliq1TargetWellPosition", user));
                logInfo("current record well loc is: " + sixChannelRec.getValue("Aliq1TargetWellPosition", user));
                dataFieldValueMap.put("DropletReading", sixChannelRec.getValue("DropletReading", user));
                dataFieldValueMap.put("ExperimentType", sixChannelRec.getValue("ExperimentType", user));
                dataFieldValueMap.put("Sampledescription1", sixChannelRec.getValue("Sampledescription1", user));
                dataFieldValueMap.put("Sampledescription2", sixChannelRec.getValue("Sampledescription2", user));
                dataFieldValueMap.put("Sampledescription3", sixChannelRec.getValue("Sampledescription3", user));
                dataFieldValueMap.put("Sampledescription4", sixChannelRec.getValue("Sampledescription4", user));
                dataFieldValueMap.put("ExemplarSampleType", sixChannelRec.getValue("ExemplarSampleType", user));
                dataFieldValueMap.put("SupermixName", sixChannelRec.getValue("SupermixName", user));
                dataFieldValueMap.put("AssayType", sixChannelRec.getValue("AssayType", user));
                dataFieldValueMap.put("TargetName", "mPTGER2");
                dataFieldValueMap.put("TargetType", sixChannelRec.getValue("TargetType", user));
                dataFieldValueMap.put("SignalCh1", "None");
                if (dataFieldValueMap.get("TargetName").toString().trim().equalsIgnoreCase("mPTGER2")) {
                    dataFieldValueMap.put("SignalCh2", "HEX");
                }
                dataFieldValueMap.put("SignalCh3", sixChannelRec.getValue("SignalCh3", user));
                dataFieldValueMap.put("SignalCh4", sixChannelRec.getValue("SignalCh4", user));
                dataFieldValueMap.put("SignalCh5", sixChannelRec.getValue("SignalCh5", user));
                dataFieldValueMap.put("SignalCh6", sixChannelRec.getValue("SignalCh6", user));
                dataFieldValueMap.put("ReferenceCopies", sixChannelRec.getValue("ReferenceCopies", user));
                dataFieldValueMap.put("WellNotes", sixChannelRec.getValue("WellNotes", user));
                dataFieldValueMap.put("Plot", sixChannelRec.getValue("Plot", user));
                dataFieldValueMap.put("RdqConversionFactor", sixChannelRec.getValue("RdqConversionFactor", user));

                for (DataRecord sample : attachedSamples) {
                    logInfo("parent sample igo id = " + sample.getParentsOfType("Sample", user).get(0).getStringVal("SampleId", user));
                    if (sixChannelRec.getStringVal("SampleId", user).equals(sample.getParentsOfType("Sample", user).get(0).getStringVal("SampleId", user))) {
                        logInfo("Adding duplicate, second channel, records to DdPcrProtocol1SixChannels");
                        ddPcrSixChannelRecords.add(sample.addChild("DdPcrProtocol1SixChannels", dataFieldValueMap, user));
                        break;
                    }
                }
            }
            activeTask.addAttachedDataRecords(ddPcrSixChannelRecords);
            this.activeTask.getTask().getTaskOptions().put("_DUPLICATE RECORDS CREATED", "");
        } catch (NotFound | IoError e) {
            throw new RuntimeException(e);
        }
        return new PluginResult(true);
    }
}
