package org.agui.quarkus.adapters;

import com.agui.community.core.event.ActivityDeltaEvent;
import com.agui.community.core.event.ActivitySnapshotEvent;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.event.MessagesSnapshotEvent;
import com.agui.community.core.event.MetaEvent;
import com.agui.community.core.event.RawEvent;
import com.agui.community.core.event.ReasoningEncryptedValueEvent;
import com.agui.community.core.event.ReasoningEndEvent;
import com.agui.community.core.event.ReasoningMessageChunkEvent;
import com.agui.community.core.event.ReasoningMessageContentEvent;
import com.agui.community.core.event.ReasoningMessageEndEvent;
import com.agui.community.core.event.ReasoningMessageStartEvent;
import com.agui.community.core.event.ReasoningStartEvent;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.event.StateDeltaEvent;
import com.agui.community.core.event.StateSnapshotEvent;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.StepStartedEvent;
import com.agui.community.core.event.TextMessageChunkEvent;
import com.agui.community.core.event.TextMessageContentEvent;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallChunkEvent;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.OutcomeType;
import com.agui.community.core.interrupt.ResumeStatus;
import com.agui.community.core.interrupt.RunOutcome;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.community.core.message.AssistantMessage;
import com.agui.community.core.message.DeveloperMessage;
import com.agui.community.core.message.Message;
import com.agui.community.core.message.ReasoningMessage;
import com.agui.community.core.message.Role;
import com.agui.community.core.message.SystemMessage;
import com.agui.community.core.message.ToolCall;
import com.agui.community.core.message.ToolMessage;
import com.agui.community.core.message.UserMessage;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Jackson polymorphic MixIns and wire-value enum codecs for {@code com.agui.community.core} records.
 */
public final class AgUiJacksonMixins {

    private AgUiJacksonMixins() {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = RunStartedEvent.class, name = "RUN_STARTED"),
            @JsonSubTypes.Type(value = RunFinishedEvent.class, name = "RUN_FINISHED"),
            @JsonSubTypes.Type(value = RunErrorEvent.class, name = "RUN_ERROR"),
            @JsonSubTypes.Type(value = StepStartedEvent.class, name = "STEP_STARTED"),
            @JsonSubTypes.Type(value = StepFinishedEvent.class, name = "STEP_FINISHED"),
            @JsonSubTypes.Type(value = TextMessageStartEvent.class, name = "TEXT_MESSAGE_START"),
            @JsonSubTypes.Type(value = TextMessageContentEvent.class, name = "TEXT_MESSAGE_CONTENT"),
            @JsonSubTypes.Type(value = TextMessageEndEvent.class, name = "TEXT_MESSAGE_END"),
            @JsonSubTypes.Type(value = TextMessageChunkEvent.class, name = "TEXT_MESSAGE_CHUNK"),
            @JsonSubTypes.Type(value = ToolCallStartEvent.class, name = "TOOL_CALL_START"),
            @JsonSubTypes.Type(value = ToolCallArgsEvent.class, name = "TOOL_CALL_ARGS"),
            @JsonSubTypes.Type(value = ToolCallEndEvent.class, name = "TOOL_CALL_END"),
            @JsonSubTypes.Type(value = ToolCallChunkEvent.class, name = "TOOL_CALL_CHUNK"),
            @JsonSubTypes.Type(value = ToolCallResultEvent.class, name = "TOOL_CALL_RESULT"),
            @JsonSubTypes.Type(value = ReasoningStartEvent.class, name = "REASONING_START"),
            @JsonSubTypes.Type(value = ReasoningEndEvent.class, name = "REASONING_END"),
            @JsonSubTypes.Type(value = ReasoningMessageStartEvent.class, name = "REASONING_MESSAGE_START"),
            @JsonSubTypes.Type(value = ReasoningMessageContentEvent.class, name = "REASONING_MESSAGE_CONTENT"),
            @JsonSubTypes.Type(value = ReasoningMessageEndEvent.class, name = "REASONING_MESSAGE_END"),
            @JsonSubTypes.Type(value = ReasoningMessageChunkEvent.class, name = "REASONING_MESSAGE_CHUNK"),
            @JsonSubTypes.Type(value = ReasoningEncryptedValueEvent.class, name = "REASONING_ENCRYPTED_VALUE"),
            @JsonSubTypes.Type(value = StateSnapshotEvent.class, name = "STATE_SNAPSHOT"),
            @JsonSubTypes.Type(value = StateDeltaEvent.class, name = "STATE_DELTA"),
            @JsonSubTypes.Type(value = MessagesSnapshotEvent.class, name = "MESSAGES_SNAPSHOT"),
            @JsonSubTypes.Type(value = ActivitySnapshotEvent.class, name = "ACTIVITY_SNAPSHOT"),
            @JsonSubTypes.Type(value = ActivityDeltaEvent.class, name = "ACTIVITY_DELTA"),
            @JsonSubTypes.Type(value = RawEvent.class, name = "RAW"),
            @JsonSubTypes.Type(value = CustomEvent.class, name = "CUSTOM"),
            @JsonSubTypes.Type(value = MetaEvent.class, name = "META_EVENT")
    })
    public interface EventMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "role")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = DeveloperMessage.class, name = "developer"),
            @JsonSubTypes.Type(value = SystemMessage.class, name = "system"),
            @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
            @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
            @JsonSubTypes.Type(value = ToolMessage.class, name = "tool"),
            @JsonSubTypes.Type(value = ReasoningMessage.class, name = "reasoning")
    })
    public interface MessageMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SuccessOutcome.class, name = "success"),
            @JsonSubTypes.Type(value = InterruptOutcome.class, name = "interrupt")
    })
    public interface RunOutcomeMixin {
    }

    public interface ToolCallMixin {
        @JsonGetter("type")
        String type();
    }

    public static ObjectMapper configure(ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        mapper.addMixIn(Event.class, EventMixin.class);
        mapper.addMixIn(Message.class, MessageMixin.class);
        mapper.addMixIn(RunOutcome.class, RunOutcomeMixin.class);
        mapper.addMixIn(ToolCall.class, ToolCallMixin.class);
        mapper.registerModule(buildEnumModule());
        return mapper;
    }

    private static SimpleModule buildEnumModule() {
        SimpleModule module = new SimpleModule("ag-ui");
        registerStringCodec(module, Role.class, Role::value, Role::fromValue);
        registerStringCodec(module, EventType.class, EventType::value, EventType::fromValue);
        registerStringCodec(module, OutcomeType.class, OutcomeType::value, OutcomeType::fromValue);
        registerStringCodec(module, ResumeStatus.class, ResumeStatus::value, ResumeStatus::fromValue);
        return module;
    }

    private static <E> void registerStringCodec(
            SimpleModule module,
            Class<E> type,
            Function<E, String> toWire,
            Function<String, E> fromWire) {
        module.addSerializer(type, new JsonSerializer<>() {
            @Override
            public void serialize(E value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(toWire.apply(value));
            }
        });
        module.addDeserializer(type, new JsonDeserializer<>() {
            @Override
            public E deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return fromWire.apply(p.getValueAsString());
            }
        });
    }
}
