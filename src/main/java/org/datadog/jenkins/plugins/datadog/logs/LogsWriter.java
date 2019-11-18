package org.datadog.jenkins.plugins.datadog.logs;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.datadog.jenkins.plugins.datadog.model.BuildData;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class LogsWriter {

    private final OutputStream errorStream;
    private final Run<?, ?> build;
    private final TaskListener listener;
    private final BuildData buildData;
    private final String jenkinsUrl;
    private boolean connectionBroken;
    private final Charset charset;

    public LogsWriter(Run<?, ?> run, OutputStream error, TaskListener listener, Charset charset)
            throws IOException, InterruptedException {
        this.errorStream = error != null ? error : System.err;
        this.build = run;
        this.listener = listener;
        this.buildData = getBuildData();
        this.jenkinsUrl = getJenkinsUrl();
        this.charset = charset;
    }

    /**
     * Gets the charset that Jenkins is using during this build.
     *
     * @return the charset
     */
    public Charset getCharset()
    {
        return charset;
    }

    /**
     * Sends a Datadog logs payload for a single line to the indexer.
     * @param line
     */
    public void write(List<String> line) {
        if (!isConnectionBroken() && StringUtils.isNotEmpty(String.valueOf(line))) {
            this.write(line);
        }
    }

    /**
     * Sends a Datadog logs payload containing log lines from the current build.
     * @param maxLines
     */
    public void writeBuildLog(int maxLines) {
        if (!isConnectionBroken()) {
            List<String> logLines;
            try {
                if (maxLines < 0) {
                    logLines = build.getLog(Integer.MAX_VALUE);
                } else {
                    logLines = build.getLog(maxLines);
                }
            } catch (IOException e) {
                String msg = "Unable to serialize log data.\n" +
                        ExceptionUtils.getStackTrace(e);
                logErrorMessage(msg);

                // Continue with error info as logs payload
                logLines = Arrays.asList(msg.split("\n"));
            }

            write(logLines);
        }
    }

    /**
     * @return True if errors have occurred during initialization or write.
     */
    public boolean isConnectionBroken() {
        return connectionBroken || build == null || buildData == null;
    }

    BuildData getBuildData() throws IOException, InterruptedException {
        if (build instanceof AbstractBuild) {
            return new BuildData((AbstractBuild<?, ?>) build, listener);
        } else {
            return new BuildData(build, listener);
        }
    }

    String getJenkinsUrl() {
        return Jenkins.getInstance().getRootUrl();
    }

    /**
     * Write error message to errorStream and set connectionBroken to true.
     */
    private void logErrorMessage(String msg) {
        try {
            connectionBroken = true;
            errorStream.write(msg.getBytes(charset));
            errorStream.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
