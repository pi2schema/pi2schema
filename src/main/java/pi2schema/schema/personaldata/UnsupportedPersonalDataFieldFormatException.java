package pi2schema.schema.personaldata;

public class UnsupportedPersonalDataFieldFormatException extends RuntimeException {

    private String fieldName;

    public UnsupportedPersonalDataFieldFormatException(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String getMessage() {
        return String.format("The type of the field %s is not supported to be encrypted", fieldName);
    }
}
