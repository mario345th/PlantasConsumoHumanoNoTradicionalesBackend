# Plantas Consumo - Backend

Repositorio oficial para el componente de **Backend** del proyecto **Plantas de Consumo Humano** dentro de la organización **Proyectos Ingeniería de Software**.

---

## 📌 Descripción del Proyecto
Este servicio backend gestiona la lógica de negocio, persistencia de datos y APIs REST para la consulta y registro de información sobre plantas de consumo humano no tradicionales de la región.

---

## 🌿 Flujo de Ramas y Entornos
- `main`: Rama de producción (protegida). Requiere revisión y aprobación obligatoria del equipo **DevOps** (Code Owners) mediante Pull Request.
- `qa`: Rama para pruebas y control de calidad (protegida). Requiere aprobación previa de pruebas (equipo **QA**) mediante Pull Request.
- `develop`: Rama principal de integración continua y desarrollo activo.

---

## 👥 Equipos y Responsabilidades
- **DevOps (`@ProyectosIngenieriaSoftware/devops`)**: Administración de infraestructura y custodia del despliegue en `main`.
- **QA (`@ProyectosIngenieriaSoftware/qa`)**: Certificación de calidad y validación de Pull Requests hacia `qa`.
- **Backend Núcleo & Backend Servicio**: Desarrollo activo de funcionalidades y creación de Pull Requests hacia `develop`.
# 🌿 Catálogo Botánico - Backend API

API REST construida para la gestión del catálogo de plantas de consumo humano no tradicionales. Este sistema maneja la persistencia y la lógica de negocio para la información botánica, ubicaciones geográficas y taxonomía.

## 🚀 Tecnologías y Herramientas

* **Lenguaje:** Java
* **Framework:** Spring Boot
* **Base de Datos:** PostgreSQL
* **Migraciones:** Flyway
* **Gestor de dependencias:** Maven

## ⚙️ Requisitos Previos

Antes de ejecutar el proyecto, asegúrate de tener instalado:
* Java Development Kit (JDK) 25 o superior.
* Servidor de PostgreSQL corriendo en tu máquina local.
* IntelliJ IDEA

---

## 📋 Guía para Desarrolladores
1. Clonar el repositorio:
   ```bash
   git clone https://github.com/ProyectosIngenieriaSoftware/plantas-consumo-backend.git
   ```
2. Crear una nueva rama a partir de `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/nombre-funcionalidad
   ```
3. Subir cambios y abrir Pull Request hacia `develop`:
   ```bash
   git push -u origin feature/nombre-funcionalidad
   ```