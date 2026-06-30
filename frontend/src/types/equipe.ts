export type EquipePerfil = 'GESTOR' | 'MEDICO' | 'RECEPCIONISTA';

export type EquipeUsuario = {
  id: number;
  nome: string;
  email: string;
  telefone: string | null;
  perfil: EquipePerfil;
  ativo: boolean;
  mustChangePassword: boolean;
  criadoEm: string | null;
};

export type EquipeGrupo = {
  perfil: EquipePerfil;
  titulo: string;
  usuarios: EquipeUsuario[];
};

export type EquipeResponse = {
  grupos: EquipeGrupo[];
};

export type EquipeUsuarioCreatePayload = {
  nome: string;
  email: string;
  perfil: EquipePerfil;
  telefone?: string;
  senhaTemporaria: string;
};
