package com.denizenscript.denizencore.scripts.commands.file;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.utilities.CoreConfiguration;
import com.denizenscript.denizencore.utilities.debugging.Debug;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class DeleteCommand extends AbstractCommand implements Holdable {

    public DeleteCommand() {
        setName("delete");
        setSyntax("delete [path:<path>] (recursive)");
        setRequiredArguments(1, 2);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name Delete
    // @Syntax delete [path:<path>] (recursive)
    // @Required 1
    // @Maximum 2
    // @Short Deletes a file or directory.
    // @Group file
    //
    // @Description
    // Deletes the specified file or directory from the server.
    // The path usually starts in the Denizen folder, unless an absolute path is specified and allowed by config.
    //
    // If the target is a directory and it is NOT empty, the command will fail unless 'recursive' is specified.
    // Specifying 'recursive' will delete the folder and ALL its contents.
    //
    // This command respects the "Restricted path" and "Allow file deletion" settings in Denizen's config.yml.
    //
    // @Tags
    // <util.has_file[<path>]>
    // <entry[saveName].success> returns whether the deletion was successful.
    //
    // @Usage
    // Use to delete a single file.
    // - delete path:data/temp.txt
    //
    // @Usage
    // Use to delete an empty folder.
    // - delete path:data/empty_folder/
    //
    // @Usage
    // Use to delete a folder and everything inside it (careful!).
    // - ~delete path:data/huge_backup_folder/ recursive
    // -->

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgPrefixed @ArgName("path") String path,
                                   @ArgName("recursive") boolean recursive) {

        if (!CoreConfiguration.allowFileDeletion) {
            Debug.echoError(scriptEntry, "File deletion disabled by administrator (check Denizen/config.yml).");
            scriptEntry.saveObject("success", new ElementTag("false"));
            scriptEntry.setFinished(true);
            return;
        }

        File file;
        boolean isAbsolute = path.startsWith("/") || path.matches("^[a-zA-Z]:.*");
        if (isAbsolute) {
            file = new File(path);
        } else {
            file = new File(DenizenCore.implementation.getDataFolder(), path);
        }

        if (!DenizenCore.implementation.canWriteToFile(file)) {
            Debug.echoError(scriptEntry, "Cannot delete that file/path due to Denizen config security settings (Restricted Path).");
            scriptEntry.saveObject("success", new ElementTag("false"));
            scriptEntry.setFinished(true);
            return;
        }

        if (!file.exists()) {
            Debug.echoDebug(scriptEntry, "File/Directory does not exist, nothing to delete: " + path);
            scriptEntry.saveObject("success", new ElementTag("true"));
            scriptEntry.setFinished(true);
            return;
        }

        Runnable run = () -> {
            try {
                if (file.isDirectory()) {
                    if (recursive) {
                        try (Stream<Path> walk = Files.walk(file.toPath())) {
                            walk.sorted(Comparator.reverseOrder())
                                    .map(Path::toFile)
                                    .forEach(File::delete);
                        }
                    } else {
                        if (!file.delete()) {
                            if (file.list() != null && file.list().length > 0) {
                                throw new IOException("Directory is not empty! Use 'recursive' to delete non-empty folders.");
                            } else {
                                throw new IOException("Failed to delete directory (unknown OS reason).");
                            }
                        }
                    }
                } else {
                    if (!file.delete()) {
                        throw new IOException("Failed to delete file.");
                    }
                }

                if (file.exists()) {
                    throw new IOException("Operation finished but file still exists.");
                }

                scriptEntry.saveObject("success", new ElementTag("true"));

            } catch (Exception e) {
                DenizenCore.runOnMainThread(() -> Debug.echoError(scriptEntry, "Delete Error: " + e.getMessage()));
                scriptEntry.saveObject("success", new ElementTag("false"));
            } finally {
                scriptEntry.setFinished(true);
            }
        };

        DenizenCore.runAsync(run);
    }
}