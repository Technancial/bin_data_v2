package pe.soapros.document.domain;

import java.io.File;

public interface TemplateRepository {
    File getTemplate(String uriFile);
    boolean isLocal (String uriFile);
}
