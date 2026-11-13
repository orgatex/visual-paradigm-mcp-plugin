#!/usr/bin/env python3
"""Deep audit of all diagram types with unique names."""
import requests
import time
import sys


class McpClient:
    def __init__(self, base_url="http://localhost:2026"):
        self.base_url = base_url
        self.request_id = 0

    def connect(self):
        # Streamable HTTP: every request is a POST to /mcp, no session to open.
        return True

    def call(self, method, params=None):
        self.request_id += 1
        payload = {"jsonrpc": "2.0", "id": self.request_id, "method": method, "params": params or {}}
        url = f"{self.base_url}/mcp"
        resp = requests.post(url, json=payload, timeout=30)
        result = resp.json()
        if "error" in result:
            return {"error": result["error"]}
        return result.get("result", {})

    def call_tool(self, name, arguments=None):
        result = self.call("tools/call", {"name": name, "arguments": arguments or {}})
        if result and "content" in result:
            for item in result["content"]:
                if item.get("type") == "text":
                    return item["text"]
        return str(result)


def main():
    c = McpClient()
    if not c.connect():
        print("FAIL: Cannot connect"); sys.exit(1)

    result = c.call("initialize", {"protocolVersion": "2024-11-05", "capabilities": {},
                                    "clientInfo": {"name": "audit", "version": "1.0"}})
    print(f"Server: {result['serverInfo']['name']}")

    ts = str(int(time.time()))
    stats = {"passed": 0, "failed": 0}
    errors = []

    def check(label, condition, detail=""):
        if condition:
            stats["passed"] += 1
            print(f"  PASS: {label}")
        else:
            stats["failed"] += 1
            errors.append(f"{label}: {detail}")
            print(f"  FAIL: {label} -- {detail}")

    # ================================================================
    # 1. USE CASE DIAGRAM
    # ================================================================
    UC = f"AUDIT_UC_{ts}"
    print(f"\n{'='*60}")
    print(f"1. USE CASE DIAGRAM: {UC}")
    print(f"{'='*60}")

    r = c.call_tool("createUseCaseDiagram", {"diagramName": UC})
    check("createUseCaseDiagram", "Created" in r, r)

    # Actors
    r = c.call_tool("addActor", {"actorName": "Thu thu", "diagramName": UC})
    check("addActor Thu thu", "Added" in r, r)
    r = c.call_tool("addActor", {"actorName": "Quan ly", "diagramName": UC})
    check("addActor Quan ly", "Added" in r, r)

    # Use cases
    ucs = ["Quan ly sach", "Muon sach", "Tra sach", "Quan ly the ban doc",
           "Xem lich su", "Tim kiem sach", "Xem thong tin", "Sua thong tin",
           "Them sach moi", "Xoa sach", "Kiem tra the", "Kiem tra so luong",
           "Tinh phi phat", "Dang ky the"]
    for uc in ucs:
        r = c.call_tool("addUseCase", {"useCaseName": uc, "diagramName": UC})
        check(f"addUseCase {uc}", "Added" in r, r)

    # Generalization
    r = c.call_tool("addRelationship", {"diagramName": UC, "sourceName": "Quan ly",
        "targetName": "Thu thu", "relationshipType": "Generalization"})
    check("Generalization Quan ly -> Thu thu", "Added" in r, r)

    # Associations
    for uc in ["Quan ly sach", "Quan ly the ban doc"]:
        r = c.call_tool("addRelationship", {"diagramName": UC, "sourceName": "Quan ly",
            "targetName": uc, "relationshipType": "Association"})
        check(f"Association Quan ly -> {uc}", "Added" in r, r)
    for uc in ["Muon sach", "Tra sach", "Xem lich su"]:
        r = c.call_tool("addRelationship", {"diagramName": UC, "sourceName": "Thu thu",
            "targetName": uc, "relationshipType": "Association"})
        check(f"Association Thu thu -> {uc}", "Added" in r, r)

    # Includes
    for src, tgt in [("Quan ly sach", "Tim kiem sach"), ("Quan ly sach", "Xem thong tin"),
                     ("Muon sach", "Kiem tra the"), ("Muon sach", "Kiem tra so luong"),
                     ("Tra sach", "Tinh phi phat")]:
        r = c.call_tool("addRelationship", {"diagramName": UC, "sourceName": src,
            "targetName": tgt, "relationshipType": "Include"})
        check(f"Include {src} -> {tgt}", "Added" in r, r)

    # Extends
    for src, tgt in [("Sua thong tin", "Quan ly sach"), ("Them sach moi", "Quan ly sach"),
                     ("Xoa sach", "Quan ly sach"), ("Dang ky the", "Quan ly the ban doc")]:
        r = c.call_tool("addRelationship", {"diagramName": UC, "sourceName": src,
            "targetName": tgt, "relationshipType": "Extend"})
        check(f"Extend {src} -> {tgt}", "Added" in r, r)

    r = c.call_tool("autoLayoutDiagram", {"diagramName": UC})
    check("autoLayoutDiagram UC", "Layout" in r or "layout" in r or "success" in r.lower() or "error" not in r.lower(), r)

    report = c.call_tool("generateUseCaseReport", {"diagramName": UC})
    check("report has 2 actors", "Actors (2):" in report, report[:200])
    check("report has 14 use cases", "Use Cases (14):" in report, report[:200])
    check("report has relationships", "Relationships (15):" in report, report[:300])
    check("report shows Thu thu", "Thu thu" in report, report[:300])
    check("report shows Include", "Include:" in report, report[:500])
    check("report shows Extend", "Extend:" in report, report[:500])
    check("report shows Association", "Association:" in report, report[:500])
    check("report shows Generalization", "Generalization:" in report, report[:500])

    elems = c.call_tool("getDiagramElements", {"diagramName": UC})
    check("getDiagramElements shows Actor", "Actor: Thu thu" in elems, elems[:300])
    check("getDiagramElements shows UseCase", "UseCase: Muon sach" in elems, elems[:300])

    # ================================================================
    # 2. CLASS DIAGRAM
    # ================================================================
    CLS = f"AUDIT_CLS_{ts}"
    print(f"\n{'='*60}")
    print(f"2. CLASS DIAGRAM: {CLS}")
    print(f"{'='*60}")

    r = c.call_tool("createClassDiagram", {"diagramName": CLS})
    check("createClassDiagram", "Created" in r, r)

    for cls in ["FrmQuanLySach", "FrmMuonSach", "SachDao", "BanDocDao", "Sach", "BanDoc"]:
        r = c.call_tool("addClass", {"diagramName": CLS, "className": cls})
        check(f"addClass {cls}", "Added" in r, r)

    for name, typ, vis in [("maSach","int","private"),("tenSach","String","private"),("tacGia","String","private")]:
        r = c.call_tool("addAttribute", {"className": "Sach", "attributeName": name, "attributeType": typ, "visibility": vis})
        check(f"addAttribute Sach.{name}", "Added" in r, r)

    for name, ret, params in [("findAll","List<Sach>",""),("findById","Sach","maSach:int"),("save","boolean","sach:Sach")]:
        r = c.call_tool("addOperation", {"className": "SachDao", "operationName": name, "returnType": ret, "params": params})
        check(f"addOperation SachDao.{name}", "Added" in r, r)

    r = c.call_tool("addAssociation", {"diagramName": CLS, "fromClass": "FrmQuanLySach", "toClass": "SachDao",
        "fromMultiplicity": "1", "toMultiplicity": "1"})
    check("addAssociation Frm->Dao", "Added" in r, r)
    r = c.call_tool("addAssociation", {"diagramName": CLS, "fromClass": "SachDao", "toClass": "Sach",
        "fromMultiplicity": "1", "toMultiplicity": "*"})
    check("addAssociation Dao->Entity", "Added" in r, r)
    r = c.call_tool("addGeneralization", {"diagramName": CLS, "fromClass": "FrmMuonSach", "toClass": "FrmQuanLySach"})
    check("addGeneralization", "Added" in r, r)

    r = c.call_tool("autoLayoutDiagram", {"diagramName": CLS})
    check("autoLayoutDiagram CLS", "error" not in r.lower(), r)

    report = c.call_tool("generateClassReport", {"diagramName": CLS})
    check("report has 6 classes", "Classes (6):" in report, report[:200])
    check("report shows Sach attrs", "maSach" in report, report[:500])
    check("report shows SachDao ops", "findAll" in report, report[:500])
    check("report shows FrmQuanLySach", "FrmQuanLySach" in report, report[:300])

    elems = c.call_tool("getDiagramElements", {"diagramName": CLS})
    check("getDiagramElements shows Class", "Class: Sach" in elems, elems[:500])
    check("getDiagramElements shows attributes", "maSach" in elems, elems[:500])
    check("getDiagramElements shows operations", "findAll" in elems, elems[:500])

    # ================================================================
    # 3. SEQUENCE DIAGRAM
    # ================================================================
    SEQ = f"AUDIT_SEQ_{ts}"
    print(f"\n{'='*60}")
    print(f"3. SEQUENCE DIAGRAM: {SEQ}")
    print(f"{'='*60}")

    r = c.call_tool("createSequenceDiagram", {"diagramName": SEQ})
    check("createSequenceDiagram", "Created" in r, r)

    for ll in ["User", "Controller", "Service", "Database"]:
        r = c.call_tool("addLifeline", {"diagramName": SEQ, "lifelineName": ll, "className": ll})
        check(f"addLifeline {ll}", "Added" in r, r)

    msgs = [
        ("User", "Controller", "login(username, password)", "1", "synch"),
        ("Controller", "Service", "authenticate(user)", "2", "synch"),
        ("Service", "Database", "queryUser(name)", "3", "synch"),
        ("Database", "Service", "return User", "4", "synch"),
        ("Service", "Controller", "return AuthResult", "5", "synch"),
        ("Controller", "User", "showDashboard()", "6", "asynch"),
    ]
    for fromLL, toLL, name, seq, typ in msgs:
        r = c.call_tool("addMessage", {"diagramName": SEQ, "fromLifeline": fromLL, "toLifeline": toLL,
            "messageName": name, "sequenceNumber": seq, "messageType": typ})
        check(f"msg {seq}: {fromLL}->{toLL}", "Added" in r, r)

    # Self message
    r = c.call_tool("addMessage", {"diagramName": SEQ, "fromLifeline": "Controller", "toLifeline": "Controller",
        "messageName": "validate()", "sequenceNumber": "7", "messageType": "synch"})
    check("self message Controller->Controller", "Added" in r, r)

    # Combined fragment
    r = c.call_tool("addCombinedFragment", {"diagramName": SEQ, "operator": "alt",
        "guard": "user == null", "coveredLifelines": "Controller,Service"})
    check("addCombinedFragment alt", "Added" in r, r)

    r = c.call_tool("autoLayoutDiagram", {"diagramName": SEQ})
    check("autoLayoutDiagram SEQ", "error" not in r.lower(), r)

    report = c.call_tool("generateSequenceReport", {"diagramName": SEQ})
    check("report has 4 lifelines", "Lifelines (4):" in report, report[:200])
    check("report has 7 messages", "Messages (7):" in report, report[:200])
    check("report shows User", "User" in report, report[:300])
    check("report shows Controller", "Controller" in report, report[:300])
    check("report shows self", "(self)" in report, report[:500])
    check("report shows async", "(async)" in report, report[:500])
    check("report shows from->to", "User -> Controller" in report, report[:500])

    elems = c.call_tool("getDiagramElements", {"diagramName": SEQ})
    check("getDiagramElements shows Lifeline", "Lifeline:" in elems, elems[:500])

    # ================================================================
    # 4. ERD
    # ================================================================
    ERD = f"AUDIT_ERD_{ts}"
    print(f"\n{'='*60}")
    print(f"4. ERD: {ERD}")
    print(f"{'='*60}")

    r = c.call_tool("createErd", {"diagramName": ERD})
    check("createErd", "Created" in r, r)

    for t in ["Students", "Courses", "Enrollments"]:
        r = c.call_tool("addTable", {"diagramName": ERD, "tableName": t})
        check(f"addTable {t}", "Added" in r, r)

    for name, typ, length, scale, pk, nullable in [
        ("student_id", "INT", 11, 0, True, False),
        ("name", "VARCHAR", 255, 0, False, False),
        ("email", "VARCHAR", 255, 0, False, True),
    ]:
        r = c.call_tool("addColumn", {"tableName": "Students", "columnName": name, "columnType": typ,
            "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})
        check(f"addColumn Students.{name}", "Added" in r, r)

    for name, typ, length, scale, pk, nullable in [
        ("course_id", "INT", 11, 0, True, False),
        ("title", "VARCHAR", 255, 0, False, False),
        ("credits", "INT", 11, 0, False, False),
    ]:
        r = c.call_tool("addColumn", {"tableName": "Courses", "columnName": name, "columnType": typ,
            "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})
        check(f"addColumn Courses.{name}", "Added" in r, r)

    for name, typ, length, scale, pk, nullable in [
        ("enrollment_id", "INT", 11, 0, True, False),
        ("student_id", "INT", 11, 0, False, False),
        ("course_id", "INT", 11, 0, False, False),
        ("grade", "VARCHAR", 10, 0, False, True),
    ]:
        r = c.call_tool("addColumn", {"tableName": "Enrollments", "columnName": name, "columnType": typ,
            "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})
        check(f"addColumn Enrollments.{name}", "Added" in r, r)

    r = c.call_tool("addForeignKey", {"diagramName": ERD, "fromTable": "Enrollments", "toTable": "Students",
        "fromColumn": "student_id", "toColumn": "student_id", "relationshipName": "FK_Enroll_Student"})
    check("addFK Enrollments->Students", "Added" in r, r)
    r = c.call_tool("addForeignKey", {"diagramName": ERD, "fromTable": "Enrollments", "toTable": "Courses",
        "fromColumn": "course_id", "toColumn": "course_id", "relationshipName": "FK_Enroll_Course"})
    check("addFK Enrollments->Courses", "Added" in r, r)

    r = c.call_tool("autoLayoutDiagram", {"diagramName": ERD})
    check("autoLayoutDiagram ERD", "error" not in r.lower(), r)

    report = c.call_tool("generateErdReport", {"diagramName": ERD})
    check("report has 3 tables", "Tables (3):" in report, report[:200])
    check("report has 2 FKs", "Foreign Keys (2):" in report, report[:500])
    check("report shows Students cols", "student_id" in report, report[:500])
    check("report shows Courses cols", "title" in report, report[:500])
    check("report shows FK Enroll->Student", "Enrollments" in report and "Students" in report, report[:800])

    elems = c.call_tool("getDiagramElements", {"diagramName": ERD})
    check("getDiagramElements shows Table", "Table:" in elems, elems[:500])
    check("getDiagramElements shows columns", "student_id" in elems, elems[:500])

    # ================================================================
    # 5. MANAGEMENT TOOLS
    # ================================================================
    print(f"\n{'='*60}")
    print(f"5. MANAGEMENT TOOLS")
    print(f"{'='*60}")

    r = c.call_tool("listDiagrams")
    check("listDiagrams shows UC", UC in r, r[:300])
    check("listDiagrams shows Class", CLS in r, r[:300])
    check("listDiagrams shows Sequence", SEQ in r, r[:300])
    check("listDiagrams shows ERD", ERD in r, r[:300])

    r = c.call_tool("getElementCounts", {"diagramName": UC})
    check("getElementCounts UC", "Actor" in r and "UseCase" in r, r[:300])

    # ================================================================
    # SUMMARY
    # ================================================================
    print(f"\n{'='*60}")
    print(f"AUDIT RESULTS")
    print(f"{'='*60}")
    print(f"  Passed: {stats['passed']}")
    print(f"  Failed: {stats['failed']}")
    if errors:
        print(f"\n  ERRORS:")
        for e in errors:
            print(f"    - {e}")
    print()


if __name__ == "__main__":
    main()
