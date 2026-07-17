package ma.hbdev.rh.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;

final class MotsClesUtils {

  private MotsClesUtils() {}

  static List<String> versListe(JsonNode node) {
    List<String> resultat = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(n -> resultat.add(n.asText()));
    }
    return resultat;
  }

  static JsonNode versJsonNode(List<String> motsCles, ObjectMapper objectMapper) {
    if (motsCles == null) {
      return null;
    }
    ArrayNode tableau = objectMapper.createArrayNode();
    motsCles.forEach(tableau::add);
    return tableau;
  }
}
