package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.i18n.LocaleUtils;
import org.slf4j.helpers.MessageFormatter;

import java.util.Objects;

@Getter
@Setter
public class ProgressState<T> {

    private T data;

    private Type type;

    private String message;

    private Object extra;

    public static <T> ProgressState<T> error(Throwable e) {
        ProgressState<T> state = new ProgressState<>();
        state.setType(Type.error);
        state.setMessage(e.getLocalizedMessage());
        return state;
    }

    public static <T> ProgressState<T> progress(String message, float progress) {
        ProgressState<T> state = new ProgressState<>();
        state.setType(Type.progress);
        state.setMessage(LocaleUtils.resolveMessage(message));
        state.setExtra(progress);
        return state;
    }


    public static <T> ProgressState<T> log(String level, String message, Object[] args) {
        ProgressState<T> state = new ProgressState<>();
        state.setType(Type.log);
        Throwable error = MessageFormatter.getThrowableCandidate(args);

        if (null != error) {
            args = MessageFormatter.trimmedCopy(args);
        }
        String i18nMaybe = LocaleUtils.resolveMessage(message, args);
        // 匹配了国际化
        if (!Objects.equals(message, i18nMaybe)) {
            state.setMessage(i18nMaybe);
        } else {
            state.setMessage(MessageFormatter.arrayFormat(message, args, error).getMessage());
        }
        state.setExtra(level);
        return state;
    }

    public static <T> ProgressState<T> success(T data) {
        ProgressState<T> state = new ProgressState<>();
        state.setType(Type.success);
        state.setData(data);
        state.setExtra(1F);
        return state;
    }

    public enum Type {
        progress, log, success, error
    }

}
