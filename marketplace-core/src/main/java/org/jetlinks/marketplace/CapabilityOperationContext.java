package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 能力操作上下文.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
@NoArgsConstructor
public class CapabilityOperationContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final Object CONTEXT_KEY = CapabilityOperationContext.class;

    public static final String HEADER_OPERATION_ID = "X-Capability-Operation-Id";

    private String id;

    public CapabilityOperationContext(String id) {
        this.id = id;
    }

    public static Mono<CapabilityOperationContext> current() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(read(context)));
    }

    public static Mono<CapabilityOperationContext> currentOrCreate() {
        return current().switchIfEmpty(Mono.fromSupplier(CapabilityOperationContext::create));
    }

    public static Function<Context, Context> makeCurrent(String id) {
        return makeCurrent(new CapabilityOperationContext(id));
    }

    public static Function<Context, Context> makeCurrent(CapabilityOperationContext context) {
        return source -> source.put(CONTEXT_KEY, context);
    }

    public static Optional<CapabilityOperationContext> read(ContextView context) {
        if (context == null || !context.hasKey(CONTEXT_KEY)) {
            return Optional.empty();
        }
        Object value = context.get(CONTEXT_KEY);
        if (value instanceof CapabilityOperationContext operationContext) {
            return Optional.of(operationContext);
        }
        if (value instanceof String id) {
            return Optional.of(new CapabilityOperationContext(id));
        }
        return Optional.empty();
    }

    public static Optional<String> readId(ContextView context) {
        return read(context).map(CapabilityOperationContext::getId);
    }

    public static CapabilityOperationContext create() {
        return new CapabilityOperationContext(UUID.randomUUID().toString());
    }
}
