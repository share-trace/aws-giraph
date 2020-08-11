package sharetrace.model.identity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sharetrace.common.Wrapped;

/**
 * A generic access token intended to be used for writing data to an authenticated entity.
 *
 * @see Identifiable
 */
@Value.Immutable
@Wrapped
@JsonSerialize(as = AccessToken.class)
@JsonDeserialize(as = AccessToken.class)
public abstract class AbstractAccessToken implements Identifiable<String> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAccessToken.class);

  @Value.Check
  protected final void verifyInputArguments() {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(getId()));
  }
}
