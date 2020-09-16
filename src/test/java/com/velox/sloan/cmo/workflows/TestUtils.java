package com.velox.sloan.cmo.workflows;

import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxConnectionException;
import com.velox.util.LogWriter;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TestUtils {

    private LogWriter log;
    /**
     * Connect to a server, then execute the rest of code.
     */
    public VeloxConnection connectServer() {
        try {
            // Set up a logger for the standalone program
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdir();
            }
            DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yy");
            String filename = "Log_" + dateFormat.format(new Date()) + ".txt";
            File file = new File(logsDir, filename);
            PrintWriter printWriter = new PrintWriter(new FileWriter(file, true), true);
            try {
                LogWriter.setPrintWriter("gabow", printWriter);

                log = new LogWriter(getClass());
                // Establish a connection using information from a file, that is expected to be
                // in the directory where the program is running from in this case
                VeloxConnection connection = new VeloxConnection("Connection.txt");
                try {
                    connection.openFromFile();

                    if (connection.isConnected()) {
                        return connection;
                    }
                } catch (VeloxConnectionException e) {
                    System.out.println(ExceptionUtils.getStackTrace(e));
                }
            } finally {
                if (printWriter != null) {
                    printWriter.close();
                }
            }
        } catch (Throwable e) {
            log.logError(e);
        }
        return null;
    }
}
