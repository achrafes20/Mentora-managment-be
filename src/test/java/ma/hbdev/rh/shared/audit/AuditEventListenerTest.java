package ma.hbdev.rh.shared.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import ma.hbdev.rh.employee.ImportExecuteEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

  @Mock private JournalAuditRepository repository;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();
  @InjectMocks private AuditEventListener listener;

  @Test
  void journaliseUnEvenementExistantAvecSesDetails() {
    listener.journaliser(new ImportExecuteEvent(UUID.randomUUID(), "employes"));

    verify(repository).save(any(JournalAudit.class));
  }
}
