# CollabDebug
CollabDebug is a real-time collaborative debugging platform that lets developers edit, run, and debug code together in secure, Docker-isolated sandboxes. Built with Java Spring Boot, React, and WebSocket syncing, it brings Google Docs style collaboration to debugging workflows.
# CollabDebug -  Collaborative Debugging Sandbox

**CollabDebug** is an innovative platform that allows developers to collaborate on debugging code in real time. Multiple users can join a shared session, edit code together, set breakpoints, and view execution logs , all within isolated Docker containers to ensure security and reproducibility.

##  Why CollabDebug Stands Out
- Real-time collaborative code editing with state syncing (like Google Docs).
- Secure Dockerized sandboxes for isolated and reproducible code execution.
- Live debugging features: breakpoints, variable inspection, and logs.
- Replay and analysis of past debugging sessions with ML-driven error suggestions.
- Built from scratch using modern technologies: Java Spring Boot, Docker, React, WebSocket, and Deep Java Library.

##  Tech Stack
- **Backend**: Java 17, Spring Boot, Docker Java API, PostgreSQL, Redis, JWT Authentication.
- **Frontend**: React, Monaco Editor (VSCode-like interface), WebSocket communication.
- **Security**: Isolated containers, resource constraints, sanitized input handling.
- **Future Enhancements**: CRDT-based collaborative editing, ML-powered debugging suggestions, scalability improvements.

##  Features (Phase 1 MVP)
✅ User registration and authentication  
✅ Create and manage debugging sessions  
✅ Code upload and execution within Docker containers  
✅ Real-time code editing with WebSocket-based syncing  
✅ Output logs streamed to users instantly


##  How to Run Locally

### Prerequisites
- Java 17+
- Docker Desktop
- Node.js and npm/yarn
- PostgreSQL and Redis installed locally

### Steps
1. Clone the repository:  
   `git clone https://github.com/your-username/collabdebug.git`
2. Start Docker Desktop.
3. Run the backend:  
   `cd backend && ./mvnw spring-boot:run`
4. Run the frontend:  
   `cd frontend && npm install && npm start`
5. Open `http://localhost:3000` and start collaborating!

##  Future Roadmap
- Add multi-language debugging support.
- Implement CRDT for conflict-free editing.
- Integrate machine learning models for real-time error suggestions.
- Enhance scalability with Kubernetes and distributed WebSocket brokers.

---

