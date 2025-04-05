package pi2schema.schema.providers.avro.personaldata;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;

import java.nio.ByteBuffer;

public final class DeepCopier {

    private final Kryo kryo;

    public DeepCopier() {
        this.kryo = new Kryo();
        kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
        kryo.setRegistrationRequired(false);

        var byteBufferSerializer = new ByteBufferSerializer();
        kryo.register(ByteBuffer.class, byteBufferSerializer);
        try {
            var heapBufferClass = Class.forName("java.nio.HeapByteBuffer");
            kryo.register(heapBufferClass, byteBufferSerializer);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("java.nio.HeapByteBuffer not found", e);
        }
    }

    public <T> T copy(T object) {
        return kryo.copy(object);
    }

    final class ByteBufferSerializer extends Serializer<ByteBuffer> {

        @Override
        public void write(Kryo kryo, Output output, ByteBuffer buffer) {
            output.writeInt(buffer.capacity());
            output.writeInt(buffer.position());
            output.writeInt(buffer.limit());

            var dup = buffer.duplicate();
            dup.position(0);
            dup.limit(dup.capacity());

            byte[] data = new byte[dup.remaining()];
            dup.get(data);
            output.writeBytes(data);
        }

        @Override
        public ByteBuffer read(Kryo kryo, Input input, Class<? extends ByteBuffer> type) {
            int capacity = input.readInt();
            int position = input.readInt();
            int limit = input.readInt();

            byte[] data = input.readBytes(capacity);
            var buffer = ByteBuffer.allocate(capacity);
            buffer.put(data);
            buffer.position(position);
            buffer.limit(limit);
            return buffer;
        }

        @Override
        public ByteBuffer copy(Kryo kryo, ByteBuffer original) {
            var copy = ByteBuffer.allocate(original.capacity());
            int originalPosition = original.position();
            int originalLimit = original.limit();

            original.rewind();
            copy.put(original);
            copy.position(originalPosition);
            copy.limit(originalLimit);

            original.position(originalPosition);
            original.limit(originalLimit);
            return copy;
        }
    }
}
