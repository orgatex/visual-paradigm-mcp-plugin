#!/usr/bin/env python3
"""Create DE01 diagrams with fresh names to verify the pipeline."""
import json
import requests
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
        payload = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params or {}
        }
        url = f"{self.base_url}/mcp"
        resp = requests.post(url, json=payload, timeout=30)
        result = resp.json()
        if "error" in result:
            print(f"ERROR: {result['error']}")
            return None
        return result.get("result", {})

    def call_tool(self, name, arguments=None):
        result = self.call("tools/call", {"name": name, "arguments": arguments or {}})
        if result and "content" in result:
            for item in result["content"]:
                if item.get("type") == "text":
                    return item["text"]
        return str(result)


def main():
    client = McpClient()
    print("Connecting to MCP server...")
    if not client.connect():
        print("Failed to connect!")
        sys.exit(1)
    print(f"Connected to {client.base_url}/mcp")

    # Initialize
    result = client.call("initialize", {
        "protocolVersion": "2024-11-05",
        "capabilities": {},
        "clientInfo": {"name": "test-de01-fresh", "version": "1.0"}
    })
    print(f"Server: {result['serverInfo']['name']}")

    # Use fresh diagram names
    UC = "DE01v2 - UC"
    CLS = "DE01v2 - Class"
    SEQ = "DE01v2 - Sequence"
    ERD = "DE01v2 - ERD"

    # ============================================================
    # 1. USE CASE DIAGRAM
    # ============================================================
    print(f"\n=== {UC} ===\n")

    client.call_tool("createUseCaseDiagram", {"diagramName": UC})
    client.call_tool("addActor", {"actorName": "Thu thu", "diagramName": UC})
    client.call_tool("addActor", {"actorName": "Quan ly", "diagramName": UC})

    ucs = [
        "Quan ly sach", "Muon sach", "Tra sach", "Quan ly the ban doc",
        "Xem lich su muon tra", "Tim kiem sach", "Xem thong tin sach",
        "Sua thong tin sach", "Them sach moi", "Xoa sach",
        "Kiem tra the ban doc", "Kiem tra so luong sach muon",
        "Tinh phi phat", "Dang ky the ban doc"
    ]
    for uc in ucs:
        client.call_tool("addUseCase", {"useCaseName": uc, "diagramName": UC})

    # Generalization
    client.call_tool("addRelationship", {
        "diagramName": UC, "sourceName": "Quan ly",
        "targetName": "Thu thu", "relationshipType": "Generalization"
    })

    # Associations
    for uc in ["Quan ly sach", "Quan ly the ban doc"]:
        client.call_tool("addRelationship", {
            "diagramName": UC, "sourceName": "Quan ly",
            "targetName": uc, "relationshipType": "Association"
        })
    for uc in ["Muon sach", "Tra sach", "Xem lich su muon tra"]:
        client.call_tool("addRelationship", {
            "diagramName": UC, "sourceName": "Thu thu",
            "targetName": uc, "relationshipType": "Association"
        })

    # Includes
    for src, tgt in [
        ("Quan ly sach", "Tim kiem sach"),
        ("Quan ly sach", "Xem thong tin sach"),
        ("Muon sach", "Kiem tra the ban doc"),
        ("Muon sach", "Kiem tra so luong sach muon"),
        ("Tra sach", "Tinh phi phat"),
    ]:
        client.call_tool("addRelationship", {
            "diagramName": UC, "sourceName": src,
            "targetName": tgt, "relationshipType": "Include"
        })

    # Extends
    for src, tgt in [
        ("Sua thong tin sach", "Quan ly sach"),
        ("Them sach moi", "Quan ly sach"),
        ("Xoa sach", "Quan ly sach"),
        ("Dang ky the ban doc", "Quan ly the ban doc"),
    ]:
        client.call_tool("addRelationship", {
            "diagramName": UC, "sourceName": src,
            "targetName": tgt, "relationshipType": "Extend"
        })

    client.call_tool("autoLayoutDiagram", {"diagramName": UC})
    print(client.call_tool("generateUseCaseReport", {"diagramName": UC}))

    # ============================================================
    # 2. CLASS DIAGRAM
    # ============================================================
    print(f"\n=== {CLS} ===\n")

    client.call_tool("createClassDiagram", {"diagramName": CLS})

    for cls in ["FrmQuanLySach", "FrmQuanLyBanDoc", "FrmMuonSach", "FrmTraSach", "FrmTimKiemSach"]:
        client.call_tool("addClass", {"diagramName": CLS, "className": cls})
    for cls in ["SachDao", "BanDocDao", "PhieuMuonDao", "ChiTietPhieuMuonDao", "TheBanDocDao"]:
        client.call_tool("addClass", {"diagramName": CLS, "className": cls})
    for cls in ["Sach", "BanDoc", "PhieuMuon", "ChiTietPhieuMuon", "TheBanDoc"]:
        client.call_tool("addClass", {"diagramName": CLS, "className": cls})

    entity_attrs = {
        "Sach": [("maSach","int","private"),("tenSach","String","private"),("tacGia","String","private"),("namXuatBan","int","private"),("giaBia","double","private"),("soLuong","int","private")],
        "BanDoc": [("maBanDoc","int","private"),("ten","String","private"),("ngaySinh","Date","private"),("diaChi","String","private"),("soDienThoai","String","private")],
        "PhieuMuon": [("maPhieuMuon","int","private"),("ngayMuon","Date","private"),("hanTra","Date","private"),("trangThai","String","private")],
        "ChiTietPhieuMuon": [("maChiTiet","int","private"),("soLuongMuon","int","private"),("ngayTra","Date","private"),("tienPhat","double","private")],
        "TheBanDoc": [("maThe","int","private"),("ngayCap","Date","private"),("hanSuDung","Date","private")],
    }
    for cls, attrs in entity_attrs.items():
        for name, typ, vis in attrs:
            client.call_tool("addAttribute", {"className": cls, "attributeName": name, "attributeType": typ, "visibility": vis})

    dao_ops = {
        "SachDao": [("findAll","List<Sach>",""),("findById","Sach","maSach:int"),("findByName","List<Sach>","tenSach:String"),("save","boolean","sach:Sach"),("delete","boolean","maSach:int")],
        "BanDocDao": [("findAll","List<BanDoc>",""),("findById","BanDoc","maBanDoc:int"),("save","boolean","banDoc:BanDoc"),("delete","boolean","maBanDoc:int")],
        "PhieuMuonDao": [("findAll","List<PhieuMuon>",""),("findById","PhieuMuon","maPhieuMuon:int"),("save","boolean","phieuMuon:PhieuMuon")],
        "ChiTietPhieuMuonDao": [("findByPhieuMuon","List<ChiTietPhieuMuon>","maPhieuMuon:int"),("save","boolean","ct:ChiTietPhieuMuon")],
        "TheBanDocDao": [("findByBanDoc","TheBanDoc","maBanDoc:int"),("save","boolean","the:TheBanDoc")],
    }
    for cls, ops in dao_ops.items():
        for name, ret, params in ops:
            client.call_tool("addOperation", {"className": cls, "operationName": name, "returnType": ret, "params": params})

    for frm, dao in [("FrmQuanLySach","SachDao"),("FrmQuanLyBanDoc","TheBanDocDao"),("FrmMuonSach","PhieuMuonDao"),("FrmTraSach","PhieuMuonDao"),("FrmTimKiemSach","SachDao")]:
        client.call_tool("addAssociation", {"diagramName": CLS, "fromClass": frm, "toClass": dao, "fromMultiplicity": "1", "toMultiplicity": "1"})

    for dao, ent, mult in [("SachDao","Sach","*"),("BanDocDao","BanDoc","*"),("PhieuMuonDao","PhieuMuon","*"),("ChiTietPhieuMuonDao","ChiTietPhieuMuon","*"),("TheBanDocDao","TheBanDoc","1")]:
        client.call_tool("addAssociation", {"diagramName": CLS, "fromClass": dao, "toClass": ent, "fromMultiplicity": "1", "toMultiplicity": mult})

    for frm, to, fMult, tMult, name in [("PhieuMuon","BanDoc","*","1","docGia"),("ChiTietPhieuMuon","PhieuMuon","*","1","phieuMuon"),("ChiTietPhieuMuon","Sach","*","1","sach"),("TheBanDoc","BanDoc","1","1","banDoc")]:
        client.call_tool("addAssociation", {"diagramName": CLS, "fromClass": frm, "toClass": to, "fromMultiplicity": fMult, "toMultiplicity": tMult, "name": name})

    client.call_tool("autoLayoutDiagram", {"diagramName": CLS})
    print(client.call_tool("generateClassReport", {"diagramName": CLS}))

    # ============================================================
    # 3. SEQUENCE DIAGRAM
    # ============================================================
    print(f"\n=== {SEQ} ===\n")

    client.call_tool("createSequenceDiagram", {"diagramName": SEQ})
    for ll in ["Quan ly", "FrmQuanLySach", "FrmTimKiemSach", "SachDao", "Sach"]:
        client.call_tool("addLifeline", {"diagramName": SEQ, "lifelineName": ll, "className": ll})

    messages = [
        ("Quan ly","FrmQuanLySach","chon menu quan ly sach","1","synch"),
        ("FrmQuanLySach","FrmQuanLySach","hien thi trang quan ly","2","synch"),
        ("Quan ly","FrmQuanLySach","chon chinh sua thong tin sach","3","synch"),
        ("FrmQuanLySach","FrmTimKiemSach","hien thi giao dien tim kiem","4","synch"),
        ("Quan ly","FrmTimKiemSach","nhap ten sach va click tim kiem","5","synch"),
        ("FrmTimKiemSach","SachDao","timKiemSach(tenSach)","6","synch"),
        ("SachDao","Sach","lay danh sach sach phu hop","7","synch"),
        ("Sach","SachDao","tra ve danh sach sach","8","synch"),
        ("SachDao","FrmTimKiemSach","tra ve ket qua tim kiem","9","synch"),
        ("FrmTimKiemSach","Quan ly","hien thi danh sach sach","10","synch"),
        ("Quan ly","FrmTimKiemSach","chon sua mot sach","11","synch"),
        ("FrmTimKiemSach","FrmQuanLySach","hien thi giao dien sua sach","12","synch"),
        ("Quan ly","FrmQuanLySach","nhap thong tin moi va click cap nhat","13","synch"),
        ("FrmQuanLySach","SachDao","capNhatSach(sach)","14","synch"),
        ("SachDao","Sach","luu thong tin vao CSDL","15","synch"),
        ("Sach","SachDao","tra ve thanh cong","16","asynch"),
        ("SachDao","FrmQuanLySach","tra ve ket qua luu","17","asynch"),
        ("FrmQuanLySach","Quan ly","thong bao thanh cong","18","asynch"),
    ]
    for fromLL, toLL, name, seq, typ in messages:
        client.call_tool("addMessage", {"diagramName": SEQ, "fromLifeline": fromLL, "toLifeline": toLL, "messageName": name, "sequenceNumber": seq, "messageType": typ})

    client.call_tool("autoLayoutDiagram", {"diagramName": SEQ})
    print(client.call_tool("generateSequenceReport", {"diagramName": SEQ}))

    # ============================================================
    # 4. ERD
    # ============================================================
    print(f"\n=== {ERD} ===\n")

    client.call_tool("createErd", {"diagramName": ERD})
    for t in ["Sach", "BanDoc", "PhieuMuon", "ChiTietPhieuMuon", "TheBanDoc"]:
        client.call_tool("addTable", {"diagramName": ERD, "tableName": t})

    for name, typ, length, scale, pk, nullable in [
        ("maSach","INT",11,0,True,False),("tenSach","VARCHAR",255,0,False,False),
        ("tacGia","VARCHAR",255,0,False,False),("namXuatBan","INT",4,0,False,True),
        ("giaBia","DECIMAL",10,2,False,True),("soLuong","INT",11,0,False,False),
    ]:
        client.call_tool("addColumn", {"tableName": "Sach", "columnName": name, "columnType": typ, "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})

    for name, typ, length, scale, pk, nullable in [
        ("maBanDoc","INT",11,0,True,False),("ten","VARCHAR",255,0,False,False),
        ("ngaySinh","DATE",0,0,False,True),("diaChi","VARCHAR",500,0,False,True),
        ("soDienThoai","VARCHAR",20,0,False,True),
    ]:
        client.call_tool("addColumn", {"tableName": "BanDoc", "columnName": name, "columnType": typ, "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})

    for name, typ, length, scale, pk, nullable in [
        ("maPhieuMuon","INT",11,0,True,False),("ngayMuon","DATE",0,0,False,False),
        ("hanTra","DATE",0,0,False,False),("trangThai","VARCHAR",50,0,False,True),
        ("maBanDoc","INT",11,0,False,False),
    ]:
        client.call_tool("addColumn", {"tableName": "PhieuMuon", "columnName": name, "columnType": typ, "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})

    for name, typ, length, scale, pk, nullable in [
        ("maChiTiet","INT",11,0,True,False),("maPhieuMuon","INT",11,0,False,False),
        ("maSach","INT",11,0,False,False),("soLuongMuon","INT",11,0,False,False),
        ("ngayTra","DATE",0,0,False,True),("tienPhat","DECIMAL",10,2,False,True),
    ]:
        client.call_tool("addColumn", {"tableName": "ChiTietPhieuMuon", "columnName": name, "columnType": typ, "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})

    for name, typ, length, scale, pk, nullable in [
        ("maThe","INT",11,0,True,False),("maBanDoc","INT",11,0,False,False),
        ("ngayCap","DATE",0,0,False,False),("hanSuDung","DATE",0,0,False,False),
    ]:
        client.call_tool("addColumn", {"tableName": "TheBanDoc", "columnName": name, "columnType": typ, "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable})

    for fromT, toT, fromC, toC, name in [
        ("PhieuMuon","BanDoc","maBanDoc","maBanDoc","FK_PhieuMuon_BanDoc"),
        ("ChiTietPhieuMuon","PhieuMuon","maPhieuMuon","maPhieuMuon","FK_CTPM_PhieuMuon"),
        ("ChiTietPhieuMuon","Sach","maSach","maSach","FK_CTPM_Sach"),
        ("TheBanDoc","BanDoc","maBanDoc","maBanDoc","FK_TheBanDoc_BanDoc"),
    ]:
        client.call_tool("addForeignKey", {"diagramName": ERD, "fromTable": fromT, "toTable": toT, "fromColumn": fromC, "toColumn": toC, "relationshipName": name})

    client.call_tool("autoLayoutDiagram", {"diagramName": ERD})
    print(client.call_tool("generateErdReport", {"diagramName": ERD}))

    print("\n=== ALL DE01v2 DIAGRAMS CREATED ===")


if __name__ == "__main__":
    main()
