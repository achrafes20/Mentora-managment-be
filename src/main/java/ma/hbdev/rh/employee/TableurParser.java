package ma.hbdev.rh.employee;

import org.springframework.web.multipart.MultipartFile;

/** Un format de fichier tableur supporté par l'import (EF-EMP-07). */
interface TableurParser {

  boolean supporte(MultipartFile fichier);

  TableurBrut parser(MultipartFile fichier);
}
