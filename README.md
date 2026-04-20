# ResumeForge AI - Backend

Production-grade Spring Boot backend for ResumeForge AI SaaS platform.

## Technology Stack

- **Java 21**
- **Spring Boot 3.2.5**
- **PostgreSQL** (Database)
- **Flyway** (Database Migrations)
- **JWT** (Authentication)
- **OpenRouter API** (AI Features)
- **Razorpay** (Payment Gateway)
- **Resend** (Email Service)

## Features

### Authentication & Authorization
- User registration with email verification (OTP)
- JWT-based authentication
- Password reset flow
- Role-based access control (USER, ADMIN)

### Resume Management
- CRUD operations for resumes
- Version history with snapshots
- Multiple template support
- JSON-based flexible schema

### AI Features (OpenRouter Integration)
- Content rewriting
- Bullet point improvement
- Summary generation
- Skill extraction
- Job-specific tailoring
- ATS scoring
- Cover letter generation
- LinkedIn optimization
- Grammar checking
- Interview preparation

### Export Features
- PDF export
- DOCX export
- TXT export (ATS-safe)
- Export history tracking
- Free tier limits (3 exports/day)

### Payment System
- Razorpay integration
- Payment verification with signature validation
- Premium subscription management
- Invoice email generation

### Referral System
- Unique referral code generation
- Reward tracking
- Anti-abuse validation

### Admin Panel
- User management
- Payment analytics
- AI usage statistics
- Referral analytics

## Setup Instructions

### Prerequisites
- Java 21
- PostgreSQL 14+
- Maven 3.8+

### Installation

1. Clone the repository
2. Copy `.env.example` to `.env` and configure:
   ```bash
   cp .env.example .env
   ```

3. Update database credentials in `.env`:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/resumeforge
   SPRING_DATASOURCE_USERNAME=your_username
   SPRING_DATASOURCE_PASSWORD=your_password
   ```

4. Configure API keys:
   - OpenRouter API key
   - Razorpay credentials
   - Resend email API key

5. Build the project:
   ```bash
   mvn clean install
   ```

6. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The backend will start on `http://localhost:8080`

### Database Migration

Flyway migrations run automatically on startup. Manual migration:
```bash
mvn flyway:migrate
```

## API Documentation

### Public Endpoints
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `POST /api/auth/verify-email-otp` - Email verification
- `POST /api/auth/forgot-password` - Request password reset
- `POST /api/auth/reset-password` - Reset password
- `POST /api/contact` - Contact form submission

### Protected Endpoints (Requires JWT)
- `GET /api/auth/me` - Get current user
- `GET /api/resumes` - List all resumes
- `POST /api/resumes` - Create resume
- `PUT /api/resumes/{id}` - Update resume
- `DELETE /api/resumes/{id}` - Delete resume
- `GET /api/resumes/{id}/history` - Get version history
- `POST /api/ai/*` - AI features (rewrite, bullets, summary, etc.)
- `GET /api/export/status` - Export status
- `GET /api/export/download/{id}` - Download resume
- `POST /api/payments/create` - Create payment order
- `POST /api/payments/verify` - Verify payment
- `GET /api/premium/status` - Premium status
- `GET /api/referral/status` - Referral status

### Admin Endpoints (Requires ADMIN role)
- `GET /api/admin/stats` - Dashboard statistics
- `GET /api/admin/users` - User list
- `POST /api/admin/users/{id}/role` - Set user role
- `POST /api/admin/users/{id}/toggle-premium` - Toggle premium
- `GET /api/admin/payments` - Payment history
- `GET /api/admin/ai-stats` - AI usage statistics

## Project Structure

```
src/main/java/com/resumeforge/ai/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── dto/            # Data Transfer Objects
├── entity/         # JPA entities
├── exception/      # Custom exceptions
├── repository/     # JPA repositories
├── security/       # Security configuration, JWT
├── service/        # Business logic
└── util/           # Utility classes

src/main/resources/
├── db/migration/   # Flyway SQL migrations
└── application.properties
```

## Environment Variables

See `.env.example` for all required environment variables.

## Production Deployment

1. Set `spring.jpa.hibernate.ddl-auto=validate` in production
2. Use strong JWT secret (256-bit minimum)
3. Enable HTTPS
4. Configure proper CORS origins
5. Set up database backups
6. Monitor logs and performance
7. Rate limit API endpoints

## Security Features

- JWT token-based authentication
- Password hashing with BCrypt
- OTP rate limiting
- Payment signature verification
- SQL injection prevention (JPA)
- CORS configuration
- Role-based authorization

## License

Proprietary - ResumeForge AI
