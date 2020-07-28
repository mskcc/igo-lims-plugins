package com.velox.sloan.cmo.workflows.utils;

import com.sun.org.slf4j.internal.Logger;
import com.sun.org.slf4j.internal.LoggerFactory;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import java.rmi.RemoteException;

public class DataAccessUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataAccessUtils.class);

    /**
     * Safely retrieves a String value from a dataRecord. Returns empty string on error.
     *
     * @param record
     * @param key
     * @param user
     * @return
     */
    public static String getRecordStringValue(DataRecord record, String key, User user) {
        try {
            if (record.getValue(key, user) != null) {
                return record.getStringVal(key, user);
            }
        } catch (NotFound | RemoteException | NullPointerException e) {
            LOGGER.error(String.format("Failed to get (String) key %s from Data Record: %d", key, record.getRecordId()));
            return "";
        }
        return "";
    }
}