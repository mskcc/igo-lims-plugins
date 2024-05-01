package com.velox.sloan.cmo.workflows.IgoLimsPluginUtils;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.datatype.TemporaryDataType;
import com.velox.api.util.ServerException;
import com.velox.api.datatype.datatypelayout.DataFormComponent;
import com.velox.api.datatype.datatypelayout.DataTypeLayout;
import com.velox.api.datatype.datatypelayout.DataTypeTabDefinition;
import com.velox.api.exception.recoverability.serverexception.UnrecoverableServerException;
import com.velox.api.datatype.fielddefinition.FieldDefinitionPosition;
import com.velox.api.datatype.fielddefinition.VeloxFieldDefinition;
import com.velox.api.plugin.PluginLogger;
import com.velox.api.util.ServerException;
import com.velox.api.user.User;
import com.velox.api.util.ClientCallbackOperations;
import com.velox.api.util.ServerException;
import com.velox.sloan.cmo.recmodels.RequestModel;
import com.velox.sloan.cmo.recmodels.SampleModel;
import com.velox.sloan.cmo.recmodels.SeqAnalysisSampleQCModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class will contain all the common methods which are often used repeatedly across different plugins.
 *
 * @author sharmaa1@mskcc.org ~Ajay Sharma
 */
public class IgoLimsPluginUtils {


    private final Pattern SPECIAL_CHARACTER_REGEX = Pattern.compile("^[a-zA-Z0-9_-]*$");
    private final Pattern SPECIAL_CHARACTER_REGEX_FOR_POOLS = Pattern.compile("^[a-zA-Z0-9,_-]*$");

    private final List<String> LIBRARY_SAMPLE_TYPES = Arrays.asList("cdna library", "dna library", "cfdna library", "pooled library");
    private final String POOLEDNORMAL_IDENTIFIER = "POOLEDNORMAL";
    private final String CONTROL_IDENTIFIER = "CTRL";

    /**
     * Method to check if a file has .csv extension
     *
     * @param fileName
     * @return true/false
     */
    public boolean isCsvFile(String fileName) {
        return fileName.toLowerCase().endsWith(".csv");
    }

    /**
     * Method to process byte data to Strings.
     *
     * @param fileContent
     * @return
     * @throws IOException
     */
    public List<String> readDataFromCsvFile(byte[] fileContent) throws IOException {
        List<String> rowDataValues = new ArrayList<>();
        InputStream dataStream = new ByteArrayInputStream(fileContent);
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(dataStream, StandardCharsets.UTF_8))) {
            String temp;
            while ((temp = fileReader.readLine()) != null) { //to check that there are no empty lines at end of file
                String rowData;
                rowData = temp;
                rowDataValues.add(rowData);
            }
        }
        return rowDataValues;
    }

    /**
     * Read csv file data into byte array
     *
     * @param fileName
     * @return byte[]
     */
    public byte[] readCsvFileToBytes(String fileName) {
        File file = new File(Objects.requireNonNull(IgoLimsPluginUtils
                .class.getClassLoader().getResource(fileName)).getPath());
        byte[] bytesArray = new byte[(int) file.length()];
        FileInputStream fileIn;
        try {
            fileIn = new FileInputStream(file);
            fileIn.read(bytesArray);
            fileIn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytesArray;
    }

    /**
     * Method to read data when users upload multiple CSV files.
     *
     * @param fileNames
     * @return Combined data from multiples CSV files
     * @throws ServerException
     * @throws IOException
     */
    public List<String> readDataFromFiles(List<String> fileNames, ClientCallbackOperations clientCallback) throws ServerException, RemoteException {
        List<String> combinedFileData = new ArrayList<>();
        for (String file : fileNames) {
            try {
                List<String> data = readDataFromCsvFile(clientCallback.readBytes(file));
                combinedFileData.addAll(data);
            } catch (ServerException e) {
                clientCallback.displayError(String.format("ServerException -> Error while reading data from uploaded file '%s':\n%s", file, ExceptionUtils.getStackTrace(e)));
            } catch (IOException e) {
                clientCallback.displayError(String.format("IOException -> Error while reading data from uploaded file '%s':\n%s", file, ExceptionUtils.getStackTrace(e)));
            }
        }
        return combinedFileData;
    }

    /**
     * Method to check if file has data other than header row
     *
     * @param fileData
     * @return true/false
     */
    public boolean csvFileHasData(List<String> fileData) {
        return fileData.size() > 1;
    }

    /**
     * Method to check if csv file has valid header row values
     *
     * @param fileData
     * @param expectedHeaderValues
     * @return true/false
     */
    public boolean csvFileHasValidHeader(List<String> fileData, List<String> expectedHeaderValues) {
        return Arrays.asList(fileData.get(0).split(",")).equals(expectedHeaderValues);
    }


    /**
     * Method to check if csv file header contains the values that are required.
     *
     * @param fileData
     * @param expectedHeaderValues
     * @return true/false
     */
    public boolean csvFileContainsRequiredHeaders(List<String> fileData, List<String> expectedHeaderValues, PluginLogger logger) {
        //return Arrays.asList(fileData.get(0).split(",")).containsAll(expectedHeaderValues);
        String[] splitData = fileData.get(0).split(",");
        List<String> trimmedData = new ArrayList<>();
        for (String data : splitData) {
            trimmedData.add(data.trim());
        }
        logger.logInfo("Trimmed data: " + trimmedData);
        logger.logInfo("Expected header values: " + expectedHeaderValues);
        printMissing(expectedHeaderValues, trimmedData, logger);
        return trimmedData.containsAll(expectedHeaderValues);
    }

    public static void printMissing(List<String> collection1, List<String> collection2, PluginLogger logger) {
        List<String> missingElements = new ArrayList<>();
        for (String element : collection1) {
            if (!collection2.contains(element)) {
                missingElements.add(element);
            }
        }

        if (missingElements.isEmpty()) {
            logger.logInfo("All elements from the first collection are present in the second collection.");
        } else {
            logger.logInfo("The following elements are missing in the second collection: " + missingElements);
        }
    }

    /**
     * Method to concatenate List of string separated by new line character '\n'.
     *
     * @param listWithValues
     * @return String of values separated in new lines
     */
    public String convertListToString(List<String> listWithValues) {
        return StringUtils.join(listWithValues, "\n");
    }


    /**
     * Method to concatenate List of string separated by comma.
     *
     * @param listWithValues
     * @return String of values separated by comma
     */
    public String convertListToCommaSeparatedString(List<String> listWithValues) {
        List<String> nonNullvalues = new ArrayList<>();
        for (String v : listWithValues) {
            if (!StringUtils.isBlank(v) && !v.trim().equalsIgnoreCase("OL")) {
                nonNullvalues.add(v);
            }
        }
        return StringUtils.join(nonNullvalues, ",");
    }

    /**
     * Method to get Map of Header values and their Index position.
     *
     * @param fileData
     * @return Map of Header value and Index position.
     */
    public Map<String, Integer> getCsvHeaderValueMap(List<String> fileData, PluginLogger logger) {
        List<String> headerRow = Arrays.asList(fileData.get(0).split(","));
        Map<String, Integer> headerValues = new HashMap<>();
        for (String value : headerRow) {
            logger.logInfo("header row: " + value.trim());
            logger.logInfo("header row value: " + headerRow.indexOf(value));
            headerValues.put(value.trim(), headerRow.indexOf(value));
        }
        return headerValues;
    }

    /**
     * Method to validate that a row in csv file has all the values that are required.
     *
     * @param rowData
     * @param requiredCsvFileColumnHeaders
     * @param headerValues
     * @return true/false
     */
    public boolean rowInCsvFileHasRequiredValues(String rowData, List<String> requiredCsvFileColumnHeaders, Map<String, Integer> headerValues) {
        List<String> rowValues = Arrays.asList(rowData.split(","));
        if (!rowValues.isEmpty()) {
            for (String value : requiredCsvFileColumnHeaders) {
                if (rowValues.size() < requiredCsvFileColumnHeaders.size() || StringUtils.isEmpty(rowValues.get(headerValues.get(value)))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Method to validate that all the rows in csv file has required values.
     *
     * @param fileData
     * @param requiredCsvFileColumnHeaders
     * @return true/false
     */
    public boolean allRowsInCsvFileHasValidData(List<String> fileData, List<String> requiredCsvFileColumnHeaders, PluginLogger logger) {
        Map<String, Integer> headerValues = getCsvHeaderValueMap(fileData, logger);
        int firstRowPositionAfterHeaderRow = 1;
        for (int i = firstRowPositionAfterHeaderRow; i <= fileData.size() - 1; i++) {
            if (!rowInCsvFileHasRequiredValues(fileData.get(i), requiredCsvFileColumnHeaders, headerValues)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method to get the Row position from given well position
     *
     * @param plateWellPosition
     * @return row position
     */
    public String getPlateWellRowPosition(String plateWellPosition) {
        return String.valueOf(plateWellPosition.charAt(0));
    }

    /**
     * Method to get the Column position from the given well position.
     *
     * @param plateWellPosition
     * @return column position
     */
    public String getPlateWellColumnPosition(String plateWellPosition) {
        if (Integer.parseInt(plateWellPosition.substring(1)) < 10) {
            return String.valueOf(plateWellPosition.charAt(2));
        }
        return plateWellPosition.substring(1);
    }

    /**
     * Method to get all the data rows from Excel File.
     *
     * @param inputData
     * @return Row data from excel file
     * @throws IOException
     */
    public List<Row> getExcelSheetDataRows(byte[] inputData) throws IOException, InvalidFormatException {
        InputStream file = new ByteArrayInputStream(inputData);
        Workbook workbook = WorkbookFactory.create(file);
        Sheet sheet = workbook.getSheetAt(0);
        List<Row> dataRows = new ArrayList<>();
        for (int rowNum = 0; rowNum < sheet.getPhysicalNumberOfRows(); rowNum++) {
            dataRows.add(sheet.getRow(rowNum));
        }
        return dataRows;
    }

    /**
     * Method to create HashMap of Header values as key and their index as value.
     *
     * @param rowData
     * @return header values and positions.
     */
    public HashMap<String, Integer> getHeaderValuesMapFromExcelRowData(List<Row> rowData) {
        HashMap<String, Integer> headerValuesMap = new HashMap<>();
        Row row = rowData.get(0);
        int i = 0;
        for (Cell cell : row) {
            headerValuesMap.put(cell.getStringCellValue().trim(), i);
            i++;
        }
        return headerValuesMap;
    }

    /**
     * Method to validate if excel file has valid extension.
     *
     * @param excelFileName
     * @return true/false
     */
    public boolean isValidExcelFile(String excelFileName) {
        return excelFileName.toLowerCase().endsWith("xlsx") || excelFileName.toLowerCase().endsWith("xls");
    }

    /**
     * Method to validate if excel file has valid Header values when compared to expected header values.
     *
     * @param dataRows
     * @param expectedHeaderValues
     * @return true/false
     */
    public boolean excelFileHasValidHeader(List<Row> dataRows, List<String> expectedHeaderValues) {
        Row headerRow = dataRows.get(0);
        List<String> fileHeaderValues = new ArrayList<>();
        int i = 0;
        for (Cell cell : headerRow) {
            fileHeaderValues.add(cell.getStringCellValue());
        }
        return fileHeaderValues.containsAll(expectedHeaderValues);
    }

    /**
     * Method to validate if the excel file has data.
     *
     * @param dataRows
     * @return true/false
     */
    public boolean excelFileHasData(List<Row> dataRows) {
        return dataRows.size() > 1;
    }


    /**
     * To check if a int value is odd.
     *
     * @param value
     * @return
     */
    public static boolean isOddValue(int value) {
        return value % 2 != 0;
    }

    /**
     * To return the quadrant position of a well on 384 well plate.
     *
     * @param wellPosition such as A1 or B13
     * @return
     */
    public static int getPlateQuadrant(String wellPosition) {
        int rowValue = wellPosition.charAt(0);
        int colValue = Integer.parseInt(wellPosition.substring(1));
        if (isOddValue(rowValue) && isOddValue(colValue)) {
            return 1;
        }
        if (!isOddValue(rowValue) && isOddValue(colValue)) {
            return 2;
        }
        if (isOddValue(rowValue) && !isOddValue(colValue)) {
            return 3;
        }
        if (!isOddValue(rowValue) && !isOddValue(colValue)) {
            return 4;
        }
        return -1;
    }

    /**
     * Method to check string for special characteres except comma and underscore.
     *
     * @param value
     * @return
     */
    public boolean hasValidCharacters(String value, Boolean isPooledSample, PluginLogger logger) {
        Matcher matcher;
        logger.logInfo("Is Pooled Sample: " + isPooledSample);
        logger.logInfo("Field value: " + value);
        if (isPooledSample) {
            matcher = SPECIAL_CHARACTER_REGEX_FOR_POOLS.matcher(value.replace("\n", "").replace("\r", ""));
        } else {
            matcher = SPECIAL_CHARACTER_REGEX.matcher(value.replace("\n", "").replace("\r", ""));
        }
        return matcher.matches();
    }

    /**
     * Method to get first parent Sample directly under the same Request in hierarchy.
     *
     * @param sample
     * @return
     * @throws ServerException
     */
    public DataRecord getParentSampleUnderRequest(DataRecord sample, User user, ClientCallbackOperations clientCallback) throws ServerException, RemoteException {
        try {
            Object requestId = sample.getValue(SampleModel.REQUEST_ID, user);
            Stack<DataRecord> sampleStack = new Stack<>();
            sampleStack.add(sample);
            do {
                DataRecord stackSample = sampleStack.pop();
                if (stackSample.getParentsOfType(RequestModel.DATA_TYPE_NAME, user).size() > 0) {
                    return stackSample;
                }
                List<DataRecord> stackSampleParentSamples = stackSample.getParentsOfType(SampleModel.DATA_TYPE_NAME, user);
                for (DataRecord sa : stackSampleParentSamples) {
                    Object saReqId = sa.getValue(SampleModel.REQUEST_ID, user);
                    if (requestId != null && saReqId != null && requestId.toString().equalsIgnoreCase(saReqId.toString())) {
                        sampleStack.push(sa);
                    }
                }
            } while (!sampleStack.isEmpty());
        } catch (Exception e) {
            String errMsg = String.format("Error while getting first parent under request for Sample with record ID %d.\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
        }
        return null;
    }

    /**
     * Method to check if Sample is user submitted Library.
     *
     * @param sample
     * @return
     * @throws ServerException
     */
    public boolean isUserLibrary(DataRecord sample, User user, ClientCallbackOperations clientCallback) throws ServerException, RemoteException {
        long recordId = sample.getRecordId();
        try {
            DataRecord parentSample = getParentSampleUnderRequest(sample, user, clientCallback);
            if (parentSample != null) {
                Object sampleType = parentSample.getValue(SampleModel.EXEMPLAR_SAMPLE_TYPE, user);
                if (sampleType == null) {
                    throw new IllegalArgumentException(String.format("Sample Type is missing on sample with recordid %d", recordId));
                }
                return sampleType != null && LIBRARY_SAMPLE_TYPES.contains(sampleType.toString().toLowerCase());
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while validating if Sample with recordid '%d' is 'User Library'.\n%s", recordId, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
        }
        return false;
    }

    /**
     * Method to get Sample matching with passed SampleId from attached Samples.
     */
    public DataRecord getSampleWithMatchingId(String sampleId, List<DataRecord> attachedSamples, String fileName, ClientCallbackOperations clientCallback, PluginLogger logger, User user) throws ServerException, RemoteException {
        DataRecord matchingSample = null;
        try {
            for (DataRecord sa : attachedSamples) {
                Object sampId = sa.getValue(SampleModel.SAMPLE_ID, user);
                if (sampId != null && sampleId.equals(sampId.toString())) {
                    matchingSample = sa;
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("Error while searching for Sample with ID '%s' in attached Samples.\n%s", sampleId, ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        }
        if (matchingSample == null) {
            String errMsg = String.format("Could not find mapping for Sample with ID '%s' in attached Samples.\nPlease double check the Sample ID's in the uploaded file(s) %s.", sampleId, fileName);
            clientCallback.displayWarning(errMsg);
            logger.logWarning(errMsg);
            return matchingSample;
        }
        return matchingSample;
    }

    /**
     * Method to get Sample Quantity value.
     *
     * @param sample
     * @return
     * @throws ServerException
     */
    public double getSampleQuantity(DataRecord sample, ClientCallbackOperations clientCallback, PluginLogger logger, User user) throws ServerException, RemoteException{
        double sampleQuantity = 0.0;
        try {
            Object concentration = sample.getValue(SampleModel.CONCENTRATION, user);
            Object volume = sample.getValue(SampleModel.VOLUME, user);
            if (concentration == null) {
                String errMsg = String.format("Error while reading 'Concentration' from Sample.\n%s", sample.getStringVal(SampleModel.SAMPLE_ID, user));
                clientCallback.displayError(errMsg);
            } else if (volume == null) {
                String errMsg = String.format("Error while reading 'Volume' from Sample.\n%s", sample.getStringVal(SampleModel.SAMPLE_ID, user));
                clientCallback.displayError(errMsg);
            } else {
                return (double) concentration * (double) volume;
            }
        } catch (RemoteException e) {
            String errMsg = String.format("Error while reading 'quantity' from Sample with recordId '%d'.\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        } catch (NotFound notFound) {
            String errMsg = String.format("NotFound while reading 'quantity' from Sample with recordId '%d'.\n%s", sample.getRecordId(), ExceptionUtils.getStackTrace(notFound));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        }
        return sampleQuantity;
    }


    /**
     * Method to validate if the QC file is bioanalyzer file or tapestation file. Both files have unique headers in the
     * first few lines.
     *
     * @param data
     * @return
     */
    public boolean isBioanalyzerFile(List<String> data, List<String> bioanalyzerIdentifiers, ClientCallbackOperations clientCallback, PluginLogger logger) throws ServerException, RemoteException {
        int countFound = 0;
        try {
            int numberOfLinesToScan = data.size() > 20 ? 20 : data.size();
            for (int i = 0; i < numberOfLinesToScan; i++) {
                List<String> lineValues = Arrays.asList(data.get(i).split(","));
                String firstVal = lineValues.get(0);
                if (!StringUtils.isBlank(firstVal) && bioanalyzerIdentifiers.contains(firstVal)) {
                    countFound++;
                }
            }
        } catch (Exception e) {
            String errMsg = String.format("%s -> Error while validating QC type for the uploaded files.\n%s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            clientCallback.displayError(errMsg);
            logger.logError(errMsg);
        }
        if (countFound == bioanalyzerIdentifiers.size() || countFound > 5) {
            return true;
        }
        return false;
    }

    /**
     * Method to check/validate headers in bioanalyzer file
     *
     * @param data
     * @param bioanalyzerHeaders
     * @param fileName
     * @param logger
     * @return
     */
    public boolean hasValidBioanalyzerHeader(List<String> data, String fileName, List<String> bioanalyzerHeaders, PluginLogger logger) {
        for (String line : data) {
            List<String> lineValues = Arrays.asList(line.split(","));
            String firstVal = lineValues.size() > 0 ? lineValues.get(0) : null;
            if (firstVal != null && bioanalyzerHeaders.contains(firstVal)) {
                logger.logInfo(String.format("Header line from file %s %s", line, fileName));
                for (String val : lineValues) {
                    if (!bioanalyzerHeaders.contains(val)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Method to check/validate headers in bioanalyzer file
     *
     * @param data
     * @param fileName
     * @param headerIdentifierValue
     * @param logger
     * @return
     */
    public Map<String, Integer> getBioanalyzerFileHeaderMap(List<String> data, String fileName, String headerIdentifierValue, PluginLogger logger) {
        Map<String, Integer> headerValueMap = new HashMap<>();
        for (String line : data) {
            List<String> lineValues = Arrays.asList(line.split(","));
            logger.logInfo("line values: " + lineValues.toString());
            String firstVal = lineValues.size() > 0 ? lineValues.get(0) : null;
            logger.logInfo("first value in line: " + firstVal);
            logger.logInfo("header identifier val: " + headerIdentifierValue);
            if (firstVal != null && firstVal.equalsIgnoreCase(headerIdentifierValue)) {
                logger.logInfo(String.format("Header line from file %s: %s", fileName, line));
                for (int i = 0; i < lineValues.size(); i++) {
                    headerValueMap.put(lineValues.get(i), i);
                }
                return headerValueMap;
            }
        }
        return headerValueMap;
    }

    /**
     * Method to set the layout on the TemporaryDataType. Without the layout the table structure is not visible in the pop up dialog.
     *
     * @param temporaryDataType
     * @param temporaryDataTypeFieldDefinitions
     * @throws ServerException
     */
    public TemporaryDataType setTempDataTypeLayout(TemporaryDataType temporaryDataType, List<VeloxFieldDefinition<?>> temporaryDataTypeFieldDefinitions, String formNameToUse, PluginLogger logger) {
        try {
            // Create form
            DataFormComponent form = new DataFormComponent(formNameToUse, formNameToUse);
            form.setCollapsed(false);
            form.setColumn(0);
            form.setColumnSpan(4);
            form.setOrder(0);
            form.setHeight(10);
            // Add fields to the form
            for (int i = 0; i < temporaryDataTypeFieldDefinitions.size(); i++) {
                logger.logInfo("adding tempdata layout");
                logger.logInfo(temporaryDataTypeFieldDefinitions.get(i).getDisplayName());
                VeloxFieldDefinition<?> fieldDef = temporaryDataTypeFieldDefinitions.get(i);
                FieldDefinitionPosition pos = new FieldDefinitionPosition(fieldDef.getDataFieldName());
                pos.setFormColumn(0);
                pos.setFormColumnSpan(4);
                pos.setOrder(i);
                pos.setFormName(formNameToUse);
                form.setFieldDefinitionPosition(pos);
            }
            // Create a tab with the form on it
            DataTypeTabDefinition tabDef = new DataTypeTabDefinition("Tab1", "Tab 1");
            tabDef.setDataTypeLayoutComponent(form);
            tabDef.setTabOrder(0);
            // Create a layout with the tab on it
            DataTypeLayout layout = new DataTypeLayout("Default", "Default", "Default layout");
            layout.setDataTypeTabDefinition(tabDef);
            // Add the layout to the TDT
            temporaryDataType.setDataTypeLayout(layout);
            logger.logInfo("layout set");
        } catch (Exception e) {
            String errMsg = String.format("%s error occured while creating DataType layout for Table dialog.\n%s", ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            logger.logError(errMsg);
        }
        return temporaryDataType;
    }

    /**
     * Method to get RecordIds from collection of DataRecords.
     *
     * @param records
     * @return
     */
    public List<Long> getRecordIds(List<DataRecord> records) {
        return records.stream().map(DataRecord::getRecordId).collect(Collectors.toList());
    }

    /**
     * Method to remove 1000 separator from CSV files. Such values in CSV files are enclosed with double quotes ("123,100")
     * This method can be used to fined such values and remove comma (",") to split the lines more efficiently and
     * extract column values.
     *
     * @param line
     * @return
     */
    public String removeThousandSeparator(String line) {
        Pattern pattern = Pattern.compile("\"([^\"]*)\"");
        String updatedLine = line;
        Matcher m = pattern.matcher(line);
        while (m.find()) {
            String val = m.group(1);
            updatedLine = updatedLine.replace(val, val.replace(",", "")).replace("\"", "");
            ;
        }
        return updatedLine;
    }

    /**
     * Method to get all SeqAnalysisSampleQC records for a sample
     *
     * @param sample
     * @param logger
     * @param user
     * @param clientCallbackOperations
     * @return
     */
    public List<DataRecord> getSequencingQcRecords(DataRecord sample, PluginLogger logger, User user, ClientCallbackOperations clientCallbackOperations) {
        List<DataRecord> sequencingQcRecords = new ArrayList<>();
        try {
            DataRecord sampleUnderRequest = getParentSampleUnderRequest(sample, user, clientCallbackOperations);
            Object requestId = sample.getValue(SampleModel.REQUEST_ID, user);
            Stack<DataRecord> sampleStack = new Stack<>();
            sampleStack.add(sampleUnderRequest);
            do {
                DataRecord stackSample = sampleStack.pop();
                DataRecord[] childSeqQc = stackSample.getChildrenOfType(SeqAnalysisSampleQCModel.DATA_TYPE_NAME, user);
                if (childSeqQc.length > 0) {
                    Collections.addAll(sequencingQcRecords, childSeqQc);
                }
                DataRecord[] stackSampleChildSamples = stackSample.getChildrenOfType(SampleModel.DATA_TYPE_NAME, user);
                for (DataRecord sa : stackSampleChildSamples) {
                    Object saReqId = sa.getValue(SampleModel.REQUEST_ID, user);
                    if (requestId != null && saReqId != null && requestId.toString().equalsIgnoreCase(saReqId.toString())) {
                        sampleStack.push(sa);
                    }
                }
            } while (!sampleStack.isEmpty());
        } catch (ServerException | RemoteException | NotFound | IoError e) {
            logger.logError(String.format("%s -> Error while getting %s records for Sample with Record Id %d,\n%s",
                    ExceptionUtils.getRootCause(e), SeqAnalysisSampleQCModel.DATA_TYPE_NAME, sample.getRecordId(), ExceptionUtils.getStackTrace(e)));
        }
        return sequencingQcRecords;
    }

    /**
     * This method will return the first DataRecord(s) with DATA_TYPE_NAME equal to @param targetDataType found in the
     * parent tree upstream. The targetDataType DataRecord is usually present as a child on one of the parents in the
     * hierarchy tree. @param parentDataType must either be same as @param record or @param record must be directly
     * under a DataRecord with DATA_TYPE_NAME equal to @param parentDataType.
     *
     * @param record
     * @param parentDataType
     * @param targetDataType
     * @return
     */
    public List<DataRecord> getRecordsOfTypeFromParents(DataRecord record, String parentDataType, String targetDataType, User user, PluginLogger logger) {
        List<DataRecord> records = new ArrayList<>();
        try {
            if (record.getChildrenOfType(targetDataType, user).length > 0) {
                return Arrays.asList(record.getChildrenOfType(targetDataType, user));
            }

            Stack<DataRecord> recordsStack = new Stack<>();
            List<DataRecord> parentRecords = record.getParentsOfType(parentDataType, user);
            recordsStack.addAll(parentRecords);
            while (!recordsStack.isEmpty()) {
                DataRecord poppedRecord = recordsStack.pop();
                if (poppedRecord.getChildrenOfType(targetDataType, user).length > 0) {
                    return Arrays.asList(poppedRecord.getChildrenOfType(targetDataType, user));
                }
                recordsStack.addAll(poppedRecord.getParentsOfType(parentDataType, user));
            }

        } catch (IoError | ServerException | RemoteException e) {
            logger.logError(String.format("%s -> Error while getting %s records for %s record with Record Id %d,\n%s",
                    ExceptionUtils.getRootCause(e), targetDataType, record.getDataTypeName(), record.getRecordId(), ExceptionUtils.getStackTrace(e)));
        }
        return records;
    }

    /**
     * Check if data record is part of records in List.
     *
     * @param dataRecords
     * @param record
     * @return
     */
    public boolean isIncludedInRecords(List<DataRecord> dataRecords, DataRecord record, PluginLogger logger) {
        try {
            return dataRecords.stream().map(DataRecord::getRecordId).collect(Collectors.toList()).contains(record.getRecordId());
        } catch (Exception e) {
            logger.logError(String.format("%s -> Error while checking if data record is included in a collection of records %s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    /**
     * Method to check if a DataRecord contains a field with given FieldName.
     *
     * @param rec
     * @param fieldName
     * @param user
     * @param logger
     * @return
     */
    public boolean hasFieldWithName(DataRecord rec, String fieldName, User user, PluginLogger logger) {
        try {
            Map<String, Object> fields = rec.getFields(user);
            return fields.containsKey(fieldName);
        } catch (Exception e) {
            logger.logError(String.format("%s -> Error while checking if data record is included in a collection of records %s", ExceptionUtils.getRootCause(e), ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    /**
     * Method to check if DataRecord is control sample or a record belonging to control.
     *
     * @param rec
     * @param logger
     * @return
     */
    public boolean isControlSample(DataRecord rec, PluginLogger logger, User user) {
        try {
            List<DataRecord> parentSamples = rec.getParentsOfType(SampleModel.DATA_TYPE_NAME, user);
            if (parentSamples.size() > 0) {
                DataRecord parent = parentSamples.get(0);
                Object isControl = parent.getValue(SampleModel.IS_CONTROL, user);
                Object igoId = parent.getValue(SampleModel.SAMPLE_ID, user);
                if (isControl != null) {
                    logger.logInfo(String.format("Is Control Sample: %s, Sample Record ID: %d", isControl, rec.getRecordId()));
                    return (boolean) isControl;
                }
                if (igoId != null && (igoId.toString().contains(POOLEDNORMAL_IDENTIFIER) || igoId.toString().contains(CONTROL_IDENTIFIER))) {
                    return true;
                }
            }
        } catch (ServerException | RemoteException | IoError | NotFound e) {
            logger.logError(String.format("%s->Error while validating %s record with record id %d is a control:\n%s", ExceptionUtils.getMessage(e), rec.getDataTypeName(), rec.getRecordId(), ExceptionUtils.getStackTrace(e)));
        }
        return false;
    }

    /**
     * Method to convert List to properly formatted values to use with SQL IN clause.
     *
     * @param vals
     * @return
     */
    public String listToSqlInClauseVal(List<Object> vals) {
        StringJoiner joiner = new StringJoiner("', '");
        for (Object val : vals) {
            joiner.add((CharSequence) val);
        }
        String format = String.format("('%s')", joiner.toString());
        return format;
    }
}
