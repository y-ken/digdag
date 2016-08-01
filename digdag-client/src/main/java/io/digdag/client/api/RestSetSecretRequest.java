package io.digdag.client.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import org.immutables.value.Value;

import static com.google.common.base.Strings.isNullOrEmpty;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@JsonSerialize(as = ImmutableRestSetSecretRequest.class)
@JsonDeserialize(as = ImmutableRestSetSecretRequest.class)
public interface RestSetSecretRequest
{
    String value();

    static RestSetSecretRequest of(String value)
    {
        Preconditions.checkArgument(!isNullOrEmpty(value));
        Preconditions.checkArgument(isAscii(value));
        return ImmutableRestSetSecretRequest.builder().value(value).build();
    }

    static boolean isAscii(String s)
    {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127) {
                return false;
            }
        }
        return true;
    }
}
