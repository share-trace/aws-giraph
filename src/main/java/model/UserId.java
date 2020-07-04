package main.java.model;

import lombok.Data;
import lombok.NonNull;

/**
 * A generic container for a user identifier.
 *
 * @param <T> Type of identification.
 */
@Data(staticConstructor = "of") public final class UserId<T> implements Identifiable<T>
{
    @NonNull private final T id;
}
