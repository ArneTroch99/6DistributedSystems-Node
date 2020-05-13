package be.uantwerpen.fti.ei.Distributed.project.Replication;

import be.uantwerpen.fti.ei.Distributed.project.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ALL")
@Repository
public class ReplicationRepository {
    private final Files files;
    private final ReplicationHTTPSender sender;
    private final Node node;

    @Autowired
    ReplicationRepository(Files files, ReplicationHTTPSender sender, Node node) {
        this.files = files;
        this.sender = sender;
        this.node = node;
    }

    void saveFile(MultipartFile file, String fileLog) {
        this.files.addReplicatedFile(file);
        this.files.addFileLog(file.getOriginalFilename(), fileLog);
    }

    void deleteFile(String filename) {
        int id = this.files.getFileLogs().get(filename).get(this.files.getFileLogs().get(filename).size());
        if (!(id == (node.getCurrentID()))) {
            sender.deleteFileByID(filename, id, node.getNamingServerIp());
        }
        this.files.removeFileLog(filename);
        new File(files.getReplicatedFolder(), filename).delete();
    }

    void shutdown() {
        for (String filename : files.getReplicatedFiles()) {
            sender.deleteFile(filename, node.getNamingServerIp());
        }
        for (final File fileEntry : files.getReplicatedFolder().listFiles()) {
            files.getFileLogs().get(fileEntry.getName()).remove(node.getCurrentID());
            sender.sendFile(fileEntry, sender.getNodeIP(node.getPreviousID(), node.getNamingServerIp()));
        }
    }


    @Scheduled(fixedRate = 500, initialDelay = 2000)
    void checkFolders() {
        List<String> temp = new ArrayList<>();
        for (final File fileEntry : files.getReplicatedFolder().listFiles()) {
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry);
            } else {
                temp.add(fileEntry.getName());
                if (!files.getReplicatedFiles().contains(fileEntry.getName())) {
                    sender.replicateFile(fileEntry, node.getNamingServerIp());
                    files.addToReplicatedFiles((fileEntry.getName()));
                }
            }
        }
        List<String> removed = new ArrayList<>();
        for (String filename : files.getReplicatedFiles()) {
            if (!temp.contains(filename)) {
                removed.add(filename);
                sender.deleteFile(filename, node.getNamingServerIp());
            }
        }
        for (String s : removed) {
            files.removeFromReplicatedFiles(s);
        }

    }

    List<String> listFilesForFolder(final File folder) {
        List<String> filenameList = new ArrayList<>();
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry);
            } else {
                filenameList.add(fileEntry.getName());
            }
        }
        return filenameList;
    }

}
