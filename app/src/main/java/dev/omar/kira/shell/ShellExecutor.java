package dev.omar.kira.shell;
import java.util.concurrent.CompletableFuture;

public interface ShellExecutor {

    ShellResult execute(String command);

}
