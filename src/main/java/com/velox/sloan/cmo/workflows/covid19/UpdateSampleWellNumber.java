package com.velox.sloan.cmo.workflows.covid19;

import com.velox.api.datafielddefinition.DataFieldDefinition;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.InvalidValue;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.plugin.PluginResult;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.server.plugin.DefaultGenericPlugin;
import com.velox.sapioutils.shared.enums.PluginOrder;
import com.velox.sloan.cmo.tag.fileexport.GenerateBioMekFileTag;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.IgoLimsPluginUtils;
import com.velox.sloan.cmo.workflows.IgoLimsPluginUtils.Tags;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.rmi.RemoteException;
import java.util.*;


/**
 * Plugin to update the Well Number on attached DataType at task to match row wise serial order of wells on a 384 well plate(1-384)
 *
 * @author sharmaa1
 */
public class UpdateSampleWellNumber extends DefaultGenericPlugin {

    private final String UPDATE_WELL_NUMBERS = "UPDATE WELL NUMBERS";
    IgoLimsPluginUtils utils = new IgoLimsPluginUtils();

    public UpdateSampleWellNumber() {
        setTaskEntry(true);
        setOrder(PluginOrder.EARLY.getOrder() + 2);
    }

    @Override
    public boolean shouldRun() throws RemoteException {
        return activeTask.getTask().getTaskOptions().containsKey(UPDATE_WELL_NUMBERS);
    }

    public PluginResult run() throws ServerException, RemoteException {
        String inputDataTypeName="";
        try {
            inputDataTypeName = activeTask.getInputDataTypeName();
            List<DataRecord> attachedProtocolRecords = activeTask.getAttachedDataRecords(inputDataTypeName, user);
            if (attachedProtocolRecords.size()==0){
                clientCallback.displayError(String.format("Could not find any attached protocol records for %s", inputDataTypeName));
            }
            setSampleSerialOrderWellNumber(attachedProtocolRecords);
        }catch (RemoteException e) {
            String errMsg = String.format("Remote Exception -> Could not update 'SampleSerialOrder' field on %s:\n%s",inputDataTypeName, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
            return new PluginResult(false);
        }
        return new PluginResult(true);
    }

    /**
     * Method to get the DestinationWell fildName from attached Input DataType at the Task.
     * @return
     * @throws ServerException
     * @throws RemoteException
     */
    private String getDestinatinWellFieldName() throws ServerException, RemoteException {
        String inputDataTypeName = null;
        String destinationWellFieldName = null;
        try {
            inputDataTypeName = activeTask.getInputDataTypeName();

        List<DataFieldDefinition> dataFieldDefinitions = dataMgmtServer.getDataFieldDefManager(user).getDataFieldDefinitions(inputDataTypeName).getDataFieldDefinitionList();

        for (DataFieldDefinition dfd : dataFieldDefinitions){
            String tag = dfd.tag;
            if (tag.matches(Tags.ALIQUOT_DESTINATION_WELL_POSITION)){
                destinationWellFieldName = dfd.dataFieldName;
            }
        }
        if (StringUtils.isBlank(destinationWellFieldName)){
            clientCallback.displayError(String.format("Could not find field with TAG %s on the attached DataType %s.", GenerateBioMekFileTag.DESTINATION_WELL, inputDataTypeName));
        }
        } catch (RemoteException e) {
            String errMsg = String.format("Error while getting Destination Well fieldname:\n%s",inputDataTypeName, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        } catch (ServerException e) {
            String errMsg = String.format("Server Exception Error while getting Destination Well fieldname:\n%s",inputDataTypeName, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logError(errMsg);
        }
        return destinationWellFieldName;
    }

    /**
     * Method to get the Well ID and its corresponding Integer Value when counted row wise.
     * @param
     * @return
     * @throws RemoteException
     * @throws ServerException
     */
    private Map<String, Integer> create384WellSerialOrder() { Map<String, Integer> wellIdToNumberMap = new HashMap<>();
       String wellIds = "ABCDEFGHIJKLMNOP";
       int maxColumnNumber = 24;
       int count=1;
       for(int i = 0; i<wellIds.length(); i++){
           for (int j=1; j<= maxColumnNumber; j++){
               String rowPos = Character.toString(wellIds.charAt(i));
               String colPos = Integer.toString(j);
               String wellPosition  = rowPos+colPos;
               wellIdToNumberMap.put(wellPosition, count);
               count+=1;
           }
       }
       return wellIdToNumberMap;
    }

    /**
     * Method to set the Well Number (Serial order) on attached Input Datatype based on Destinatin Well ID.
     * @param attachedProtocolRecords
     * @throws RemoteException
     * @throws ServerException
     * @throws NotFound
     * @throws IoError
     * @throws InvalidValue
     */
   private void setSampleSerialOrderWellNumber(List<DataRecord> attachedProtocolRecords){
        Map<String,Integer> wellIdToNumberOrder = create384WellSerialOrder();
        try {
            String inputDataTypeName = activeTask.getInputDataTypeName();
            String destinationWellFieldName = getDestinatinWellFieldName();
            for (DataRecord rec : attachedProtocolRecords) {
                if (rec.getValue(destinationWellFieldName, user) == null) {
                    String sampleId = rec.getStringVal("SampleId", user);
                    clientCallback.displayError(String.format("%s field values is NULL in attached %s DataRecords for %s. Please make sure that all samples have Destinatin Well ID's", destinationWellFieldName, inputDataTypeName, sampleId));
                } else {
                    String wellId = rec.getStringVal(destinationWellFieldName, user);
                    rec.setDataField("SampleSerialOrder", wellIdToNumberOrder.get(wellId), user);
                }
            }
        } catch (InvalidValue invalidValue) {
            logError(String.format("InvalidValue Exception -> Error while setting Serial Order for Plate wells for Covid 19 samples:\n%s", ExceptionUtils.getStackTrace(invalidValue)));
        } catch (IoError ioError) {
            logError(String.format("IoError Exception -> Error while setting Serial Order for Plate wells for Covid 19 samples:\n%s", ExceptionUtils.getStackTrace(ioError)));
        } catch (ServerException e) {
            logError(String.format("ServerException -> Error while setting Serial Order for Plate wells for Covid 19 samples:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (RemoteException e) {
            logError(String.format("RemoteException -> Error while setting Serial Order for Plate wells for Covid 19 samples:\n%s", ExceptionUtils.getStackTrace(e)));
        } catch (NotFound notFound) {
            logError(String.format("NotFound Exception -> Error while setting Serial Order for Plate wells for Covid 19 samples:\n%s", ExceptionUtils.getStackTrace(notFound)));
        }
   }
}
