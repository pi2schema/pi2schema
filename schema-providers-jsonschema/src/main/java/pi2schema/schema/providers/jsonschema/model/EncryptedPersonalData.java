package pi2schema.schema.providers.jsonschema.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents encrypted personal data structure compatible with other pi2schema providers.
 * This class mirrors the EncryptedPersonalData structure used in Avro and Protobuf implementations.
 */
public class EncryptedPersonalData {

    @JsonProperty("subjectId")
    private String subjectId;

    @JsonProperty("data")
    private byte[] data; // Base64 encoded

    @JsonProperty("personalDataFieldNumber")
    private String personalDataFieldNumber;

    @JsonProperty("usedTransformation")
    private String usedTransformation;

    @JsonProperty("initializationVector")
    private String initializationVector; // Base64 encoded

    @JsonProperty("kmsId")
    private String kmsId;

    public EncryptedPersonalData() {
        // Default constructor for Jackson
    }

    public EncryptedPersonalData(
        String subjectId,
        byte[] data,
        String personalDataFieldNumber,
        String usedTransformation,
        String initializationVector,
        String kmsId
    ) {
        this.subjectId = subjectId;
        this.data = data;
        this.personalDataFieldNumber = personalDataFieldNumber;
        this.usedTransformation = usedTransformation;
        this.initializationVector = initializationVector;
        this.kmsId = kmsId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getPersonalDataFieldNumber() {
        return personalDataFieldNumber;
    }

    public void setPersonalDataFieldNumber(String personalDataFieldNumber) {
        this.personalDataFieldNumber = personalDataFieldNumber;
    }

    public String getUsedTransformation() {
        return usedTransformation;
    }

    public void setUsedTransformation(String usedTransformation) {
        this.usedTransformation = usedTransformation;
    }

    public String getInitializationVector() {
        return initializationVector;
    }

    public void setInitializationVector(String initializationVector) {
        this.initializationVector = initializationVector;
    }

    public String getKmsId() {
        return kmsId;
    }

    public void setKmsId(String kmsId) {
        this.kmsId = kmsId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncryptedPersonalData that = (EncryptedPersonalData) o;
        return (
            Objects.equals(subjectId, that.subjectId) &&
            java.util.Arrays.equals(data, that.data) &&
            Objects.equals(personalDataFieldNumber, that.personalDataFieldNumber) &&
            Objects.equals(usedTransformation, that.usedTransformation) &&
            Objects.equals(initializationVector, that.initializationVector) &&
            Objects.equals(kmsId, that.kmsId)
        );
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(subjectId, personalDataFieldNumber, usedTransformation, initializationVector, kmsId);
        result = 31 * result + java.util.Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return (
            "EncryptedPersonalData{" +
            "subjectId='" +
            subjectId +
            '\'' +
            ", personalDataFieldNumber='" +
            personalDataFieldNumber +
            '\'' +
            ", usedTransformation='" +
            usedTransformation +
            '\'' +
            ", kmsId='" +
            kmsId +
            '\'' +
            '}'
        );
    }
}
