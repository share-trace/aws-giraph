package algorithm.format.input;

import algorithm.format.FormatUtils;
import algorithm.format.vertex.FactorVertex;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import model.contact.Contact;
import model.identity.UserGroup;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FactorVertexInputFormat extends
    TextVertexInputFormat<UserGroup, Contact, NullWritable> {

  private static final Logger log = LoggerFactory.getLogger(FactorVertexInputFormat.class);

  private static final ObjectMapper OBJECT_MAPPER = FormatUtils.getObjectMapper();

  @Override
  public final TextVertexReader createVertexReader(InputSplit inputSplit,
      TaskAttemptContext taskAttemptContext) {
    return new FactorVertexReader();
  }

  private final class FactorVertexReader extends TextVertexReader {

    @Override
    public boolean nextVertex() throws IOException, InterruptedException {
      return getRecordReader().nextKeyValue();
    }

    @Override
    public Vertex<UserGroup, Contact, NullWritable> getCurrentVertex()
        throws IOException, InterruptedException {
      Text line = getRecordReader().getCurrentValue();
      FactorVertex factorVertex = OBJECT_MAPPER.readValue(line.toString(), FactorVertex.class);
      Vertex<UserGroup, Contact, NullWritable> vertex = getConf().createVertex();
      vertex.initialize(factorVertex.getVertexId(), factorVertex.getVertexValue());
      return vertex;
    }
  }
}
