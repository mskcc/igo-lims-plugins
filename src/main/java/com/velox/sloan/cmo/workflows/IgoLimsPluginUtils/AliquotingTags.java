package com.velox.sloan.cmo.workflows.IgoLimsPluginUtils;

public interface AliquotingTags {

    String ALIQUOT_STARTING_CONCENTRATION = ".*<!--\\s*ALIQUOT\\s*STARTING\\s*CONCENTRATION\\s*-->.*";

    String ALIQUOT_STARTING_VOL = ".*<!--\\s*ALIQUOT\\s*STARTING\\s*VOLUME\\s*-->.*";

    String ALIQUOT_STARTING_MASS = ".*<!--\\s*ALIQUOT\\s*STARTING\\s*MASS\\s*-->.*";

    String ALIQUOT_SOURCE_MASS_TO_USE = ".*<!--\\s*ALIQUOT\\s*SOURCE\\s*MASS\\s*TO\\s*USE\\s*-->.*";

    String ALIQUOT_SOURCE_VOL_TO_USE = ".*<!--\\s*ALIQUOT\\s*SOURCE\\s*VOLUME\\s*TO\\s*USE\\s*-->.*";

    String ALIQUOT_DILUTANT_VOLUME_TO_USE =".*<!--\\s*ALIQUOT\\s*DILUTANT\\s*VOLUME\\s*TO\\s*USE\\s*-->.*";

    String ALIQUOT_TARGET_CONC = ".*<!--\\s*ALIQUOT\\s*TARGET\\s*CONCENTRATION\\s*-->.*";

    String ALIQUOT_TARGET_MASS = ".*<!--\\s*ALIQUOT\\s*TARGET\\s*MASS\\s*-->.*";

    String ALIQUOT_TARGET_VOL = ".*<!--\\s*ALIQUOT\\s*TARGET VOLUME\\s*-->.*";

    String ALIQUOT_SOURCE_PLATE_ID = ".*<!--\\s*ALIQUOT\\s*SOURCE\\s*PLATE\\s*ID\\s*-->.*";

    String ALIQUOT_SOURCE_WELL_POS = ".*<!--\\s*ALIQUOT\\s*SOURCE\\s*WELL\\s*POSITION\\s*-->.*";

    String ALIQUOT_SOURCE_WELL_ROW = ".*<!--\\s*ALIQUOT\\s*SOURCE\\s*WELL\\s*ROW\\s*-->.*";

    String ALIQUOT_SOURCE_WELL_COL = ".*<!--\\s*ALIQUOT\\s*SOURCE\\s*WELL\\s*COLUMN\\s*-->.*";

    String ALIQUOT_DESTINATION_PLATE_ID = ".*<!--\\s*ALIQUOT\\s*DESTINATION\\s*PLATE\\s*ID\\s*-->.*";

    String ALIQUOT_DESTINATION_WELL_POS = ".*<!--\\s*ALIQUOT\\s*DESTINATION\\s*WELL\\s*POSITION\\s*-->.*";

    String ALIQUOT_DESTINATION_POOL = ".*<!--\\s*ALIQUOT\\s*DESTINATION\\s*POOL\\s*-->.*";

    String ALIQUOT_DESTINATION_WELL_ROW = ".*<!--\\s*ALIQUOT\\s*DESTINATION\\s*WELL\\s*ROW\\s*-->.*";

    String ALIQUOT_DESTINATION_COL = ".*<!--\\s*ALIQUOT\\s*DESTINATION\\s*WELL\\s*COLUMN\\s*-->.*";

    String ALIQUOT_DESTINATION_TUBE_BARCODE = ".*<!--\\s*ALIQUOT\\s*DESTINATION\\s*TUBE\\s*BARCODE\\s*-->.*";

    String ALIQUOT_ORIGINAL_SAMPLEID = ".*<!--\\s*ALIQUOT\\s*ORIGINAL\\s*SAMPLE\\s*ID\\s*-->.*";

    String ALIQUOT_CONCENTRATION_UNITS = ".*<!--\\s*ALIQUOT\\s*CONCENTRATION\\s*UNITS\\s*-->.*";

    String ALIQUOT_NEW_CONTROL = ".*<!--\\s*ALIQUOT\\s*NEW\\s*CONTROL\\s*-->.*";

    String ALIQUOT_CONTROL_TYPE = ".*<!--\\s*ALIQUOT\\s*CONTROL\\s*TYPE\\s*-->.*";
}
