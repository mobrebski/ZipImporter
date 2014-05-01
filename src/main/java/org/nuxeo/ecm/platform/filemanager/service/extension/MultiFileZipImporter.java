/**
 *
 */
package org.nuxeo.ecm.platform.filemanager.service.extension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.nuxeo.ecm.platform.filemanager.utils.FileManagerUtils;
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

        // Get and check Zip file
        File tmp = File.createTempFile("multifilezip-importer", null);
        content.transferTo(tmp);
        ZipFile zip = getArchiveFileIfValid(tmp);
        if (zip == null) {
            tmp.delete();
            return null;
        }

        DocumentModel container = documentManager.getDocument(new PathRef(path));

        // Process each folder
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            // Get path and names
            String name = entry.getName();

            // Parse path
            Path filePath = Paths.get(name);
            Path rootPath = filePath.getParent();
            String rootName = "";
            if (rootPath != null) {
                rootName = "/" + rootPath.toString();
            }
            String fileName = filePath.getFileName().toString();

            if (entry.isDirectory()) {
                log.info(path + rootName + " / " + fileName);
                // Check if exists
                DocumentModel folder = FileManagerUtils.getExistingDocByTitle(
                        documentManager, path + rootName, fileName);

                // Create Folder if it doesn't exist already by name
                if (folder == null) {
                    log.info("Creating: " + path + rootName + " / " + fileName);
                    folder = documentManager.createDocumentModel(path
                            + rootName, fileName, "Folder");
                    folder.setPropertyValue("dc:title", fileName);
                    documentManager.createDocument(folder);
                }
            }

            else {

                // Create Files (Automatically versions duplicates by name)
                InputStream blobStream = zip.getInputStream(entry);
                Blob blob = new InputStreamBlob(blobStream);
                blob.setFilename(fileName);

                fileManagerService.createDocumentFromBlob(documentManager,
                        blob, path + rootName, overwrite, fileName);

            }
        }

        return container;
    }

}
