import { Component, inject, OnInit, signal } from '@angular/core';
import { LucideKeyRound, LucideLoaderCircle, LucidePlus, LucideTrash } from '@lucide/angular';
import { Usuario } from '../../models/usuario';
import { mensajeDeError } from '../../services/cronograma.service';
import { SesionService } from '../../services/sesion.service';
import { UsuarioService } from '../../services/usuario.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { ToastService } from '../../shared/toast/toast.service';

/** Quién puede entrar a la app. Todos pueden todo. */
@Component({
  selector: 'app-usuarios',
  imports: [LucideKeyRound, LucideLoaderCircle, LucidePlus, LucideTrash],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css',
})
export class Usuarios implements OnInit {
  private api = inject(UsuarioService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);
  sesion = inject(SesionService);

  cargando = signal(true);
  usuarios = signal<Usuario[]>([]);

  /** Alta: el formulario abierto o no. */
  creando = signal(false);
  nuevo = signal({ usuario: '', nombre: '', clave: '' });

  /** A quién se le está poniendo contraseña nueva (el propio pide la actual). */
  cambiandoClave = signal<Usuario | null>(null);
  claveActual = signal('');
  claveNueva = signal('');

  guardando = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  esYo(u: Usuario): boolean {
    return u.id === this.sesion.usuario()?.id;
  }

  cambiarNuevo(cambios: Partial<{ usuario: string; nombre: string; clave: string }>): void {
    this.nuevo.update((n) => ({ ...n, ...cambios }));
  }

  crear(): void {
    const n = this.nuevo();
    this.guardando.set(true);
    this.api.crear(n.usuario, n.nombre, n.clave).subscribe({
      next: (u) => {
        this.guardando.set(false);
        this.creando.set(false);
        this.nuevo.set({ usuario: '', nombre: '', clave: '' });
        this.toast.exito(`Listo: ${u.nombre} ya puede entrar como "${u.usuario}".`);
        this.cargar();
      },
      error: (e) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo crear.'));
      },
    });
  }

  empezarClave(u: Usuario): void {
    this.cambiandoClave.set(this.cambiandoClave()?.id === u.id ? null : u);
    this.claveActual.set('');
    this.claveNueva.set('');
  }

  guardarClave(): void {
    const u = this.cambiandoClave();
    if (!u) return;
    this.guardando.set(true);
    const pedido = this.esYo(u) ? this.api.cambiarMiClave(this.claveActual(), this.claveNueva()) : this.api.cambiarClave(u.id, this.claveNueva());
    pedido.subscribe({
      next: () => {
        this.guardando.set(false);
        this.cambiandoClave.set(null);
        this.toast.exito('Contraseña cambiada.');
      },
      error: (e) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo cambiar.'));
      },
    });
  }

  async borrar(u: Usuario): Promise<void> {
    const ok = await this.confirmacion.pedir({
      titulo: 'Borrar usuario',
      mensaje: `${u.nombre} ("${u.usuario}") ya no va a poder entrar.`,
      aceptar: 'Borrar',
      peligro: true,
    });
    if (!ok) return;
    this.api.eliminar(u.id).subscribe({
      next: () => {
        this.toast.exito('Usuario borrado.');
        this.cargar();
      },
      error: (e) => this.toast.error(mensajeDeError(e, 'No se pudo borrar.')),
    });
  }

  private cargar(): void {
    this.api.listar().subscribe({
      next: (l) => {
        this.usuarios.set(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.cargando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
