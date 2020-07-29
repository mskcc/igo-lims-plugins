package com.velox.sloan.cmo.workflows.utils;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import java.rmi.RemoteException;

public class DataAccessUtils {
    /**
     * Safely retrieves a String value from a dataRecord. Returns empty string on error.
     *
     * @param record
     * @param key
     * @param user
     * @return
     */
    public static String getRecordStringValue(DataRecord record, String key, User user) throws IllegalStateException {
        try {
            if (record.getValue(key, user) != null) {
                return record.getStringVal(key, user);
            }
        } catch (NotFound | RemoteException | NullPointerException e) {
            throw new IllegalStateException(String.format("Failed to get (String) key %s from Data Record: %d", key, record.getRecordId()));
        }
        return "";
    }
}