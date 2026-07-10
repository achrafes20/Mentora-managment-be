package ma.hbdev.rh.employee;

import org.springframework.core.io.Resource;

public record EmployeDocumentTelecharge(Resource ressource, String nomOriginal, String typeMime) {}
