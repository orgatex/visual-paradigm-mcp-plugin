#!/usr/bin/env python3
"""Test MCP server tools using official MCP client."""
import asyncio
from mcp.client.session import ClientSession
from mcp.client.sse import sse_client

async def main():
    print("Connecting to MCP server via SSE...")

    async with sse_client("http://localhost:8080/sse") as (read, write):
        async with ClientSession(read, write) as session:
            # Initialize
            result = await session.initialize()
            print(f"Connected! Server: {result.serverInfo.name} v{result.serverInfo.version}")

            # List tools
            tools = await session.list_tools()
            print(f"\nAvailable tools ({len(tools.tools)}):")
            for t in tools.tools:
                print(f"  - {t.name}: {t.description[:70] if t.description else ''}")

            print("\n--- Testing Class Diagram Tools ---\n")

            # Create class diagram
            r = await session.call_tool("createClassDiagram", {"diagramName": "Online Shopping System"})
            print(f"createClassDiagram: {r.content[0].text if r.content else '(empty)'}")

            # Add class Customer
            r = await session.call_tool("addClass", {"diagramName": "Online Shopping System", "className": "Customer"})
            print(f"addClass(Customer): {r.content[0].text if r.content else '(empty)'}")

            # Add attribute
            r = await session.call_tool("addAttribute", {
                "className": "Customer", "attributeName": "name",
                "attributeType": "String", "visibility": "private"
            })
            print(f"addAttribute(name): {r.content[0].text if r.content else '(empty)'}")

            # Add operation
            r = await session.call_tool("addOperation", {
                "className": "Customer", "operationName": "getOrders",
                "returnType": "List<Order>"
            })
            print(f"addOperation(getOrders): {r.content[0].text if r.content else '(empty)'}")

            # Add class Order
            r = await session.call_tool("addClass", {"diagramName": "Online Shopping System", "className": "Order"})
            print(f"addClass(Order): {r.content[0].text if r.content else '(empty)'}")

            # Add association
            r = await session.call_tool("addAssociation", {
                "diagramName": "Online Shopping System",
                "fromClass": "Customer", "toClass": "Order",
                "fromMult": "1", "toMult": "*"
            })
            print(f"addAssociation: {r.content[0].text if r.content else '(empty)'}")

            # Generate report
            r = await session.call_tool("generateClassReport", {"diagramName": "Online Shopping System"})
            print(f"generateClassReport:\n{r.content[0].text if r.content else '(empty)'}")

            print("\n--- Test Complete ---")

if __name__ == "__main__":
    asyncio.run(main())
