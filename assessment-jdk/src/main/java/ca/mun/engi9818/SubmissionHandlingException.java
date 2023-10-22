package ca.mun.engi9818;

public class SubmissionHandlingException extends Exception {
    private String log;

    public SubmissionHandlingException(String message) {
        super(message);
    }

    public SubmissionHandlingException(String message, Throwable cause) {
        super(message, cause);
    }

    public SubmissionHandlingException(String message, String log, Throwable cause) {
        super(message, cause);
        this.log = log;
    }

    public SubmissionHandlingException(String message, String log) {
        super(message);
        this.log = log;
    }

    public String getLog() {
        return log;
    }
}
