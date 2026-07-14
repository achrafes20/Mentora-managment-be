package ma.hbdev.rh.employee;

import org.springframework.core.io.Resource;

record EmployePhotoTelecharge(Resource ressource, String typeMime) {}
