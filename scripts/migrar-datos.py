"""
Copia la configuración de la app de tu PC a la del VPS: puestos (prioridad, si va en fijos, roles), grupos de
puestos, orden de rotación y reglas. No toca empleados ni cronogramas. Se puede correr más de una vez: lo que ya
está en el VPS se actualiza, no se duplica.

Uso (con el back de tu PC prendido):
    python scripts/migrar-datos.py              # pide el usuario y la contraseña del VPS
    python scripts/migrar-datos.py --probar     # sólo muestra qué haría, sin escribir nada

Opciones: --origen URL (por defecto http://localhost:8082), --destino URL (por defecto
https://rotaciones.parquelaserranita.online). La contraseña no se guarda en ningún lado.
"""
import argparse
import getpass
import json
import sys
import unicodedata
import urllib.error
import urllib.request


def clave(texto):
    t = unicodedata.normalize("NFD", texto or "")
    return " ".join("".join(c for c in t if unicodedata.category(c) != "Mn").lower().split())


class Api:
    def __init__(self, base, usuario, clave_):
        self.base = base.rstrip("/") + "/api/"
        self.token = None
        self.token = self.pedir("POST", "auth/login", {"usuario": usuario, "clave": clave_, "mantener": False})["token"]

    def pedir(self, metodo, ruta, cuerpo=None):
        cabeceras = {"Content-Type": "application/json; charset=utf-8"}
        if self.token:
            cabeceras["Authorization"] = "Bearer " + self.token
        datos = json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else None
        pedido = urllib.request.Request(self.base + ruta, data=datos, method=metodo, headers=cabeceras)
        try:
            with urllib.request.urlopen(pedido, timeout=30) as r:
                texto = r.read().decode("utf-8")
                return json.loads(texto) if texto else None
        except urllib.error.HTTPError as e:
            detalle = e.read().decode("utf-8", "replace")
            try:
                detalle = json.loads(detalle).get("mensaje", detalle)
            except ValueError:
                pass
            raise SystemExit(f"!! {metodo} {ruta}: {e.code} {detalle}")
        except urllib.error.URLError as e:
            raise SystemExit(f"!! No se pudo conectar con {self.base}: {e.reason}")


def main():
    args = argparse.ArgumentParser(description="Copia puestos, grupos, orden de rotación y reglas de tu PC al VPS.")
    args.add_argument("--origen", default="http://localhost:8082")
    args.add_argument("--origen-usuario", default="admin")
    args.add_argument("--origen-clave", default="admin123")
    args.add_argument("--destino", default="https://rotaciones.parquelaserranita.online")
    args.add_argument("--probar", action="store_true", help="sólo mostrar qué haría")
    a = args.parse_args()

    print(f"==> Leyendo de {a.origen}...")
    origen = Api(a.origen, a.origen_usuario, a.origen_clave)
    puestos = origen.pedir("GET", "puestos")
    grupos = origen.pedir("GET", "grupos")
    orden = origen.pedir("GET", "reglas-apertura/orden-rotacion")
    reglas = origen.pedir("GET", "reglas-apertura")
    print(f"    {len(puestos)} puestos, {len(grupos)} grupos, {len(orden)} en el orden de rotación, {len(reglas)} reglas.")

    print(f"==> Entrando a {a.destino}")
    usuario = input("    Usuario del VPS (APP_USUARIO): ").strip()
    contrasena = getpass.getpass("    Contraseña del VPS (APP_CLAVE): ")
    destino = Api(a.destino, usuario, contrasena)
    ya_puestos = {clave(p["nombre"]): p for p in destino.pedir("GET", "puestos")}
    ya_grupos = {clave(g["nombre"]): g for g in destino.pedir("GET", "grupos")}
    ya_reglas = {(r["empleados"], r["afluencia"]) for r in destino.pedir("GET", "reglas-apertura")}

    nuevos = [p for p in puestos if clave(p["nombre"]) not in ya_puestos]
    print(f"    En el VPS ya hay {len(ya_puestos)} puestos, {len(ya_grupos)} grupos y {len(ya_reglas)} reglas.")
    print(f"==> Se van a crear {len(nuevos)} puestos y actualizar {len(puestos) - len(nuevos)};"
          f" {len(grupos)} grupos; el orden de rotación; {len(reglas)} reglas"
          f" ({sum(1 for r in reglas if (r['empleados'], r['afluencia']) in ya_reglas)} ya existían y se pisan).")
    if a.probar:
        print("    --probar: no se escribió nada.")
        return
    if input("==> ¿Seguir? [s/N] ").strip().lower() not in ("s", "si", "sí"):
        print("Cancelado.")
        return

    # 1. Puestos: crear los que faltan y dejar prioridad, "en fijos" y roles como en tu PC.
    for p in puestos:
        if clave(p["nombre"]) not in ya_puestos:
            destino.pedir("POST", "puestos", {"nombre": p["nombre"], "prioridad": p["prioridad"]})
        destino.pedir("PUT", "puestos", {"nombre": p["nombre"], "prioridad": p["prioridad"]})
        destino.pedir("PUT", "puestos/fijos", {"nombre": p["nombre"], "mostrarEnFijos": p["mostrarEnFijos"]})
        if p.get("roles"):
            destino.pedir("PUT", "puestos/roles", {"nombre": p["nombre"], "roles": p["roles"], "rolResto": p.get("rolResto")})
    print(f"    Puestos: listo ({len(puestos)}).")

    # 2. Grupos (Parque): el mismo nombre se actualiza.
    for g in grupos:
        cuerpo = {"id": None, "nombre": g["nombre"], "puestos": g["puestos"]}
        existente = ya_grupos.get(clave(g["nombre"]))
        if existente:
            destino.pedir("PUT", f"grupos/{existente['id']}", dict(cuerpo, id=existente["id"]))
        else:
            destino.pedir("POST", "grupos", cuerpo)
    print(f"    Grupos: listo ({len(grupos)}).")

    # 3. Orden de rotación (antes de las reglas: al guardarlo reordena las ruedas).
    if orden:
        destino.pedir("PUT", "reglas-apertura/orden-rotacion", orden)
    print("    Orden de rotación: listo.")

    # 4. Reglas: la de la misma cantidad y afluencia se pisa.
    for r in reglas:
        destino.pedir("PUT", "reglas-apertura", dict(r, id=None))
    print(f"    Reglas: listo ({len(reglas)}).")
    print("==> Terminado. Entrá a la app del VPS y mirá Configuración > Puestos y Reglas.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nCancelado.")
