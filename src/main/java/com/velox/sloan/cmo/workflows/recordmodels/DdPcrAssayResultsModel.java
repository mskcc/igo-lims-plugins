package com.velox.sloan.cmo.workflows.recordmodels;


import com.velox.sapio.commons.exemplar.recordmodel.annotation.ExemplarDataTypeModel;
import com.velox.sapio.commons.exemplar.recordmodel.record.AbstractRecordModelWrapper;
import com.velox.sapio.commons.exemplar.recordmodel.record.RecordModel;

@ExemplarDataTypeModel(
        dataTypeName = "DdPcrAssayResults"
)
public class DdPcrAssayResultsModel extends AbstractRecordModelWrapper{
    public static final String DATA_TYPE_NAME = "DdPcrAssayResults";
    public static final String SAMPLE_ID = "SampleId";
    public static final String OTHER_SAMPLE_ID = "OtherSampleId";
    protected DdPcrAssayResultsModel(RecordModel backingModel) {
        super(backingModel);
    }
}
