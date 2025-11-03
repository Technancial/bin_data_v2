package pe.soapros.document.infrastructure.util;

public class Util {
    public static String getExtensionFile(String path){
        String extension = "";
        int i = path.lastIndexOf('.');
        if (i > 0) {
            extension = path.substring(i + 1);
        }
        return extension;
    }
}
