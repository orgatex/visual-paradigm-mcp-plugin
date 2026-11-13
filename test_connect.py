#!/usr/bin/env python3
"""Quick MCP connection test."""
import asyncio
import sys
from mcp.client.session import ClientSession
from mcp.client.streamable_http import streamablehttp_client


async def test():
    print("Connecting...")
    try:
        async with streamablehttp_client("http://localhost:2026/mcp") as (read, write, _):
            print("Got streams")
            async with ClientSession(read, write) as session:
                print("Initializing...")
                result = await session.initialize()
                print(f"Connected! Server: {result.serverInfo.name}")

                tools = await session.list_tools()
                print(f"Tools available: {len(tools.tools)}")
                for t in tools.tools[:5]:
                    print(f"  - {t.name}")
                print("  ...")
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(test())
