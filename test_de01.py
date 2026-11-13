#!/usr/bin/env python3
"""Create DE01 diagrams via MCP tools to verify the pipeline."""
import asyncio
from mcp.client.session import ClientSession
from mcp.client.streamable_http import streamablehttp_client


async def main():
    print("Connecting to MCP server via streamable HTTP...")

    async with streamablehttp_client("http://localhost:2026/mcp") as (read, write, _):
        async with ClientSession(read, write) as session:
            result = await session.initialize()
            print(f"Connected! Server: {result.serverInfo.name}")

            # ============================================================
            # 1. USE CASE DIAGRAM
            # ============================================================
            print("\n=== DE01 - Use Case Diagram ===\n")

            await session.call_tool("createUseCaseDiagram", {"diagramName": "DE01 - UC"})

            # Actors
            await session.call_tool("addActor", {"actorName": "Thu thu", "diagramName": "DE01 - UC"})
            await session.call_tool("addActor", {"actorName": "Quan ly", "diagramName": "DE01 - UC"})

            # Use cases
            ucs = [
                "Quan ly sach", "Muon sach", "Tra sach", "Quan ly the ban doc",
                "Xem lich su muon tra", "Tim kiem sach", "Xem thong tin sach",
                "Sua thong tin sach", "Them sach moi", "Xoa sach",
                "Kiem tra the ban doc", "Kiem tra so luong sach muon",
                "Tinh phi phat", "Dang ky the ban doc"
            ]
            for uc in ucs:
                await session.call_tool("addUseCase", {"useCaseName": uc, "diagramName": "DE01 - UC"})

            # Generalization: Quan ly -> Thu thu
            await session.call_tool("addRelationship", {
                "diagramName": "DE01 - UC", "sourceName": "Quan ly",
                "targetName": "Thu thu", "relationshipType": "Generalization"
            })

            # Associations: Actor -> Use Cases
            for uc in ["Quan ly sach", "Quan ly the ban doc"]:
                await session.call_tool("addRelationship", {
                    "diagramName": "DE01 - UC", "sourceName": "Quan ly",
                    "targetName": uc, "relationshipType": "Association"
                })
            for uc in ["Muon sach", "Tra sach", "Xem lich su muon tra"]:
                await session.call_tool("addRelationship", {
                    "diagramName": "DE01 - UC", "sourceName": "Thu thu",
                    "targetName": uc, "relationshipType": "Association"
                })

            # Include relationships
            includes = [
                ("Quan ly sach", "Tim kiem sach"),
                ("Quan ly sach", "Xem thong tin sach"),
                ("Muon sach", "Kiem tra the ban doc"),
                ("Muon sach", "Kiem tra so luong sach muon"),
                ("Tra sach", "Tinh phi phat"),
            ]
            for src, tgt in includes:
                await session.call_tool("addRelationship", {
                    "diagramName": "DE01 - UC", "sourceName": src,
                    "targetName": tgt, "relationshipType": "Include"
                })

            # Extend relationships
            extends = [
                ("Sua thong tin sach", "Quan ly sach"),
                ("Them sach moi", "Quan ly sach"),
                ("Xoa sach", "Quan ly sach"),
                ("Dang ky the ban doc", "Quan ly the ban doc"),
            ]
            for src, tgt in extends:
                await session.call_tool("addRelationship", {
                    "diagramName": "DE01 - UC", "sourceName": src,
                    "targetName": tgt, "relationshipType": "Extend"
                })

            await session.call_tool("autoLayoutDiagram", {"diagramName": "DE01 - UC"})

            r = await session.call_tool("generateUseCaseReport", {"diagramName": "DE01 - UC"})
            print(r.content[0].text)

            # ============================================================
            # 2. CLASS DIAGRAM
            # ============================================================
            print("\n=== DE01 - Class Diagram ===\n")

            await session.call_tool("createClassDiagram", {"diagramName": "DE01 - Class"})

            # Boundary classes
            boundaries = [
                "FrmQuanLySach", "FrmQuanLyBanDoc", "FrmMuonSach",
                "FrmTraSach", "FrmTimKiemSach"
            ]
            for cls in boundaries:
                await session.call_tool("addClass", {"diagramName": "DE01 - Class", "className": cls})

            # DAO classes
            daos = ["SachDao", "BanDocDao", "PhieuMuonDao", "ChiTietPhieuMuonDao", "TheBanDocDao"]
            for cls in daos:
                await session.call_tool("addClass", {"diagramName": "DE01 - Class", "className": cls})

            # Entity classes
            entities = ["Sach", "BanDoc", "PhieuMuon", "ChiTietPhieuMuon", "TheBanDoc"]
            for cls in entities:
                await session.call_tool("addClass", {"diagramName": "DE01 - Class", "className": cls})

            # Entity attributes
            entity_attrs = {
                "Sach": [
                    ("maSach", "int", "private"), ("tenSach", "String", "private"),
                    ("tacGia", "String", "private"), ("namXuatBan", "int", "private"),
                    ("giaBia", "double", "private"), ("soLuong", "int", "private"),
                ],
                "BanDoc": [
                    ("maBanDoc", "int", "private"), ("ten", "String", "private"),
                    ("ngaySinh", "Date", "private"), ("diaChi", "String", "private"),
                    ("soDienThoai", "String", "private"),
                ],
                "PhieuMuon": [
                    ("maPhieuMuon", "int", "private"), ("ngayMuon", "Date", "private"),
                    ("hanTra", "Date", "private"), ("trangThai", "String", "private"),
                ],
                "ChiTietPhieuMuon": [
                    ("maChiTiet", "int", "private"), ("soLuongMuon", "int", "private"),
                    ("ngayTra", "Date", "private"), ("tienPhat", "double", "private"),
                ],
                "TheBanDoc": [
                    ("maThe", "int", "private"), ("ngayCap", "Date", "private"),
                    ("hanSuDung", "Date", "private"),
                ],
            }
            for cls, attrs in entity_attrs.items():
                for name, typ, vis in attrs:
                    await session.call_tool("addAttribute", {
                        "className": cls, "attributeName": name,
                        "attributeType": typ, "visibility": vis
                    })

            # DAO operations
            dao_ops = {
                "SachDao": [
                    ("findAll", "List<Sach>", ""), ("findById", "Sach", "maSach:int"),
                    ("findByName", "List<Sach>", "tenSach:String"),
                    ("save", "boolean", "sach:Sach"), ("delete", "boolean", "maSach:int"),
                ],
                "BanDocDao": [
                    ("findAll", "List<BanDoc>", ""), ("findById", "BanDoc", "maBanDoc:int"),
                    ("save", "boolean", "banDoc:BanDoc"), ("delete", "boolean", "maBanDoc:int"),
                ],
                "PhieuMuonDao": [
                    ("findAll", "List<PhieuMuon>", ""), ("findById", "PhieuMuon", "maPhieuMuon:int"),
                    ("save", "boolean", "phieuMuon:PhieuMuon"),
                ],
                "ChiTietPhieuMuonDao": [
                    ("findByPhieuMuon", "List<ChiTietPhieuMuon>", "maPhieuMuon:int"),
                    ("save", "boolean", "ct:ChiTietPhieuMuon"),
                ],
                "TheBanDocDao": [
                    ("findByBanDoc", "TheBanDoc", "maBanDoc:int"),
                    ("save", "boolean", "the:TheBanDoc"),
                ],
            }
            for cls, ops in dao_ops.items():
                for name, ret, params in ops:
                    await session.call_tool("addOperation", {
                        "className": cls, "operationName": name,
                        "returnType": ret, "params": params
                    })

            # Associations: Boundary -> DAO
            boundary_dao = [
                ("FrmQuanLySach", "SachDao"), ("FrmQuanLyBanDoc", "TheBanDocDao"),
                ("FrmMuonSach", "PhieuMuonDao"), ("FrmTraSach", "PhieuMuonDao"),
                ("FrmTimKiemSach", "SachDao"),
            ]
            for frm, dao in boundary_dao:
                await session.call_tool("addAssociation", {
                    "diagramName": "DE01 - Class", "fromClass": frm, "toClass": dao,
                    "fromMultiplicity": "1", "toMultiplicity": "1"
                })

            # Associations: DAO -> Entity
            dao_entity = [
                ("SachDao", "Sach", "*"), ("BanDocDao", "BanDoc", "*"),
                ("PhieuMuonDao", "PhieuMuon", "*"),
                ("ChiTietPhieuMuonDao", "ChiTietPhieuMuon", "*"),
                ("TheBanDocDao", "TheBanDoc", "1"),
            ]
            for dao, ent, mult in dao_entity:
                await session.call_tool("addAssociation", {
                    "diagramName": "DE01 - Class", "fromClass": dao, "toClass": ent,
                    "fromMultiplicity": "1", "toMultiplicity": mult
                })

            # Entity associations
            entity_assocs = [
                ("PhieuMuon", "BanDoc", "*", "1", "docGia"),
                ("ChiTietPhieuMuon", "PhieuMuon", "*", "1", "phieuMuon"),
                ("ChiTietPhieuMuon", "Sach", "*", "1", "sach"),
                ("TheBanDoc", "BanDoc", "1", "1", "banDoc"),
            ]
            for frm, to, fMult, tMult, name in entity_assocs:
                await session.call_tool("addAssociation", {
                    "diagramName": "DE01 - Class", "fromClass": frm, "toClass": to,
                    "fromMultiplicity": fMult, "toMultiplicity": tMult, "name": name
                })

            await session.call_tool("autoLayoutDiagram", {"diagramName": "DE01 - Class"})

            r = await session.call_tool("generateClassReport", {"diagramName": "DE01 - Class"})
            print(r.content[0].text)

            # ============================================================
            # 3. SEQUENCE DIAGRAM
            # ============================================================
            print("\n=== DE01 - Sequence Diagram ===\n")

            await session.call_tool("createSequenceDiagram", {"diagramName": "DE01 - Sequence"})

            lifelines = ["Quan ly", "FrmQuanLySach", "FrmTimKiemSach", "SachDao", "Sach"]
            for ll in lifelines:
                await session.call_tool("addLifeline", {
                    "diagramName": "DE01 - Sequence", "lifelineName": ll, "className": ll
                })

            messages = [
                ("Quan ly", "FrmQuanLySach", "chon menu quan ly sach", "1", "synch"),
                ("FrmQuanLySach", "FrmQuanLySach", "hien thi trang quan ly", "2", "synch"),
                ("Quan ly", "FrmQuanLySach", "chon chinh sua thong tin sach", "3", "synch"),
                ("FrmQuanLySach", "FrmTimKiemSach", "hien thi giao dien tim kiem", "4", "synch"),
                ("Quan ly", "FrmTimKiemSach", "nhap ten sach va click tim kiem", "5", "synch"),
                ("FrmTimKiemSach", "SachDao", "timKiemSach(tenSach)", "6", "synch"),
                ("SachDao", "Sach", "lay danh sach sach phu hop", "7", "synch"),
                ("Sach", "SachDao", "tra ve danh sach sach", "8", "synch"),
                ("SachDao", "FrmTimKiemSach", "tra ve ket qua tim kiem", "9", "synch"),
                ("FrmTimKiemSach", "Quan ly", "hien thi danh sach sach", "10", "synch"),
                ("Quan ly", "FrmTimKiemSach", "chon sua mot sach", "11", "synch"),
                ("FrmTimKiemSach", "FrmQuanLySach", "hien thi giao dien sua sach", "12", "synch"),
                ("Quan ly", "FrmQuanLySach", "nhap thong tin moi va click cap nhat", "13", "synch"),
                ("FrmQuanLySach", "SachDao", "capNhatSach(sach)", "14", "synch"),
                ("SachDao", "Sach", "luu thong tin vao CSDL", "15", "synch"),
                ("Sach", "SachDao", "tra ve thanh cong", "16", "asynch"),
                ("SachDao", "FrmQuanLySach", "tra ve ket qua luu", "17", "asynch"),
                ("FrmQuanLySach", "Quan ly", "thong bao thanh cong", "18", "asynch"),
            ]
            for fromLL, toLL, name, seq, typ in messages:
                await session.call_tool("addMessage", {
                    "diagramName": "DE01 - Sequence", "fromLifeline": fromLL,
                    "toLifeline": toLL, "messageName": name,
                    "sequenceNumber": seq, "messageType": typ
                })

            await session.call_tool("autoLayoutDiagram", {"diagramName": "DE01 - Sequence"})

            r = await session.call_tool("generateSequenceReport", {"diagramName": "DE01 - Sequence"})
            print(r.content[0].text)

            # ============================================================
            # 4. ERD
            # ============================================================
            print("\n=== DE01 - ERD ===\n")

            await session.call_tool("createErd", {"diagramName": "DE01 - ERD"})

            tables = ["Sach", "BanDoc", "PhieuMuon", "ChiTietPhieuMuon", "TheBanDoc"]
            for t in tables:
                await session.call_tool("addTable", {"diagramName": "DE01 - ERD", "tableName": t})

            # Sach columns
            sach_cols = [
                ("maSach", "INT", 11, 0, True, False),
                ("tenSach", "VARCHAR", 255, 0, False, False),
                ("tacGia", "VARCHAR", 255, 0, False, False),
                ("namXuatBan", "INT", 4, 0, False, True),
                ("giaBia", "DECIMAL", 10, 2, False, True),
                ("soLuong", "INT", 11, 0, False, False),
            ]
            for name, typ, length, scale, pk, nullable in sach_cols:
                await session.call_tool("addColumn", {
                    "tableName": "Sach", "columnName": name, "columnType": typ,
                    "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable
                })

            # BanDoc columns
            bandoc_cols = [
                ("maBanDoc", "INT", 11, 0, True, False),
                ("ten", "VARCHAR", 255, 0, False, False),
                ("ngaySinh", "DATE", 0, 0, False, True),
                ("diaChi", "VARCHAR", 500, 0, False, True),
                ("soDienThoai", "VARCHAR", 20, 0, False, True),
            ]
            for name, typ, length, scale, pk, nullable in bandoc_cols:
                await session.call_tool("addColumn", {
                    "tableName": "BanDoc", "columnName": name, "columnType": typ,
                    "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable
                })

            # PhieuMuon columns
            pm_cols = [
                ("maPhieuMuon", "INT", 11, 0, True, False),
                ("ngayMuon", "DATE", 0, 0, False, False),
                ("hanTra", "DATE", 0, 0, False, False),
                ("trangThai", "VARCHAR", 50, 0, False, True),
                ("maBanDoc", "INT", 11, 0, False, False),
            ]
            for name, typ, length, scale, pk, nullable in pm_cols:
                await session.call_tool("addColumn", {
                    "tableName": "PhieuMuon", "columnName": name, "columnType": typ,
                    "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable
                })

            # ChiTietPhieuMuon columns
            ctpm_cols = [
                ("maChiTiet", "INT", 11, 0, True, False),
                ("maPhieuMuon", "INT", 11, 0, False, False),
                ("maSach", "INT", 11, 0, False, False),
                ("soLuongMuon", "INT", 11, 0, False, False),
                ("ngayTra", "DATE", 0, 0, False, True),
                ("tienPhat", "DECIMAL", 10, 2, False, True),
            ]
            for name, typ, length, scale, pk, nullable in ctpm_cols:
                await session.call_tool("addColumn", {
                    "tableName": "ChiTietPhieuMuon", "columnName": name, "columnType": typ,
                    "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable
                })

            # TheBanDoc columns
            the_cols = [
                ("maThe", "INT", 11, 0, True, False),
                ("maBanDoc", "INT", 11, 0, False, False),
                ("ngayCap", "DATE", 0, 0, False, False),
                ("hanSuDung", "DATE", 0, 0, False, False),
            ]
            for name, typ, length, scale, pk, nullable in the_cols:
                await session.call_tool("addColumn", {
                    "tableName": "TheBanDoc", "columnName": name, "columnType": typ,
                    "length": length, "scale": scale, "isPrimaryKey": pk, "isNullable": nullable
                })

            # Foreign keys
            fks = [
                ("PhieuMuon", "BanDoc", "maBanDoc", "maBanDoc", "FK_PhieuMuon_BanDoc"),
                ("ChiTietPhieuMuon", "PhieuMuon", "maPhieuMuon", "maPhieuMuon", "FK_CTPM_PhieuMuon"),
                ("ChiTietPhieuMuon", "Sach", "maSach", "maSach", "FK_CTPM_Sach"),
                ("TheBanDoc", "BanDoc", "maBanDoc", "maBanDoc", "FK_TheBanDoc_BanDoc"),
            ]
            for fromT, toT, fromC, toC, name in fks:
                await session.call_tool("addForeignKey", {
                    "diagramName": "DE01 - ERD", "fromTable": fromT, "toTable": toT,
                    "fromColumn": fromC, "toColumn": toC, "relationshipName": name
                })

            await session.call_tool("autoLayoutDiagram", {"diagramName": "DE01 - ERD"})

            r = await session.call_tool("generateErdReport", {"diagramName": "DE01 - ERD"})
            print(r.content[0].text)

            print("\n=== ALL DE01 DIAGRAMS CREATED ===")


if __name__ == "__main__":
    asyncio.run(main())
