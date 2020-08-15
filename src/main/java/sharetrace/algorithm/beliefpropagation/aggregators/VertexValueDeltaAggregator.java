package sharetrace.algorithm.beliefpropagation.aggregators;

import com.google.common.base.Preconditions;
import java.text.MessageFormat;
import java.util.Optional;
import org.apache.giraph.aggregators.Aggregator;
import org.apache.hadoop.io.DoubleWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VertexValueDeltaAggregator implements Aggregator<DoubleWritable> {

  private static final Logger LOGGER = LoggerFactory.getLogger(VertexValueDeltaAggregator.class);

  private static final Double INITIAL_VALUE = Double.POSITIVE_INFINITY;

  private DoubleWritable aggregatedValue = new DoubleWritable(INITIAL_VALUE);

  @Override
  public void aggregate(DoubleWritable a) {
    Preconditions.checkNotNull(a);
    setAggregatedValue(new DoubleWritable(aggregatedValue.get() + a.get()));
  }

  @Override
  public DoubleWritable createInitialValue() {
    return new DoubleWritable(INITIAL_VALUE);
  }

  @Override
  public DoubleWritable getAggregatedValue() {
    return new DoubleWritable(aggregatedValue.get());
  }

  @Override
  public void setAggregatedValue(DoubleWritable a) {
    Preconditions.checkNotNull(a);
    aggregatedValue.set(a.get());
  }

  @Override
  public void reset() {
    if (Optional.ofNullable(aggregatedValue).isPresent()) {
      aggregatedValue.set(INITIAL_VALUE);
    } else {
      aggregatedValue = new DoubleWritable(INITIAL_VALUE);
    }
  }

  @Override
  public String toString() {
    String pattern = "{0}'{'aggregatedValue={1}'}'";
    return MessageFormat.format(pattern, getClass().getSimpleName(), aggregatedValue);
  }
}
