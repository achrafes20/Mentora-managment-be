package ma.hbdev.rh.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class NoopCvAnalysisProviderTest {

  @Test
  void nestJamaisDisponibleEtLeveUneException() {
    NoopCvAnalysisProvider noop = new NoopCvAnalysisProvider();
    assertThat(noop.disponible()).isFalse();
    assertThatThrownBy(
            () ->
                noop.analyser(new ByteArrayResource("x".getBytes()), "application/pdf", null, null))
        .isInstanceOf(CvAnalysisException.class);
  }
}
