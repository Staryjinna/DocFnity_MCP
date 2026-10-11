package com.mmis.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point. STDIO transport: stdout carries only MCP JSON-RPC messages,
 * so everything diagnostic goes to stderr.
 */
public final class Main {

    static final String VERSION = "0.1.0";

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch stdinClosed = new CountDownLatch(1);
        var transport = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper(), eofSignalling(System.in, stdinClosed), System.out);

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("mmis-mcp", VERSION)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .build();

        server.addTool(pingTool());

        System.err.println("mmis-mcp " + VERSION + " ready on STDIO");
        // The transport reads stdin on its own threads; stay alive until the client closes stdin.
        stdinClosed.await();
        server.closeGracefully();
    }

    /** Wraps stdin so end-of-stream (client went away) releases the latch. */
    static InputStream eofSignalling(InputStream in, CountDownLatch latch) {
        return new FilterInputStream(in) {
            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b < 0) {
                    latch.countDown();
                }
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                int n = super.read(buf, off, len);
                if (n < 0) {
                    latch.countDown();
                }
                return n;
            }
        };
    }

    static SyncToolSpecification pingTool() {
        Tool tool = Tool.builder("ping", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false))
                .description("Health check. Returns 'pong' and the server version.")
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> CallToolResult.builder()
                        .content(List.<McpSchema.Content>of(new McpSchema.TextContent("pong (mmis-mcp " + VERSION + ")")))
                        .build())
                .build();
    }
}
