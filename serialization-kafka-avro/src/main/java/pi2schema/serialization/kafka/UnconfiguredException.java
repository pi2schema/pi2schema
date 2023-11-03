package pi2schema.serialization.kafka;

public class UnconfiguredException extends IllegalStateException{

    @Override
    public String getMessage() {
        return "When using the empty constructor which is expected for the Serializer interface," +
                "the configure method is mandatory to be called in this serializer. \n" +
                "The configure call should be made automatically if the object is instantiated by kafka." +
                "Case the object is being created manually, please ensure the call of .configure before its use";
    }
}
