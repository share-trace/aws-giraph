package main.java.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

/**
 * A generic access token intended to be used for writing data to an authenticated entity.
 */
@Value(staticConstructor = "of")
public class AccessToken<T extends Comparable<T>> implements Identifiable<T>
{
    @NonNull
    @Getter(AccessLevel.NONE)
    T token;

    @Override
    public T getId()
    {
        return token;
    }

    @Override
    public int compareTo(@NonNull Identifiable<T> o)
    {
        return token.compareTo(o.getId());
    }
}
