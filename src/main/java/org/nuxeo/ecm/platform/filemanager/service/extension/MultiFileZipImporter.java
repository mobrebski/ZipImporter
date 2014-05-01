/**
 *
 */
package org.nuxeo.ecm.platform.filemanager.service.extension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.InputStreamBlob;
import org.nuxeo.ecm.platform.types.TypeManager;

/**
 * @author mikeobrebski
 *
 */
public class MultiFileZipImporter extends AbstractFileImporter {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(MultiFileZipImporter.class);

    public static ZipFile getArchiveFileIfValid(File file) throws IOException {
        ZipFile zip;

        try {
            zip = new ZipFile(file);
        } catch (ZipException e) {
            log.debug("file is not a zipfile ! ", e);
            return null;
        } catch (IOException e) {
            log.debug("can not open zipfile ! ", e);
            return null;
        }

        return zip;
    }

    @Override
    public DocumentModel create(CoreSession documentManager, Blob content,
            String path, boolean overwrite, String filename,
            TypeManager typeService) throws ClientException, IOException {

        File tmp = File.createTempFile("multifilezip-importer", null);

        content.transferTo(tmp);

        ZipFile zip = getArchiveFileIfValid(tmp);

        if (zip == null) {
            tmp.delete();
            return null;
        }
        DocumentModel container = documentManager.getDocument(new PathRef(path));


        //Process each folder
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            //Process each directory
            ZipEntry entry = entries.nextElement();
            //Get path and names
            String name = entry.getName();

            //Parse path
            Path filePath = Paths.get(name);
            Path rootPath = filePath.getParent();
            String rootName = "";
            if (rootPath != null) {
                rootName = "/"+rootPath.toString();
            }
            String fileName = filePath.getFileName().toString();
            log.debug(path+rootName+"/"+fileName);

            if ( entry.isDirectory() ){
                DocumentModel folder = documentManager.createDocumentModel(path+rootName, fileName, "Folder");
                folder.setPropertyValue("dc:title", fileName);
                documentManager.createDocument(folder);
            }

            else {

                // create document model
                DocumentModel file = documentManager.createDocumentModel(path+rootName, fileName, "File");
                file.setPropertyValue("dc:title", fileName);
                file.setPropertyValue("file:filename", fileName);

                InputStream blobStream = zip.getInputStream(entry);
                Blob blob = new InputStreamBlob(blobStream);
                blob.setFilename(fileName);

                // update data
                file.setPropertyValue("file:content", (Serializable)blob);
                // create

                documentManager.createDocument(file);


            }
        }

        return container;
    }

}
