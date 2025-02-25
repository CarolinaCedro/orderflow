* Java 17
* KAFKA
* Micro-services
* Spring Boot/Cache
* Mongo-db
* Docker
* Gitflow


### 1. Inventory Service (Serviço de Estoque)

    Gerencia a quantidade de produtos disponíveis.
    Pode fornecer APIs para consultar a quantidade de itens no estoque e para realizar atualizações quando um pedido for feito.

### 2. Shipping Service (Serviço de Envio)

    Gerencia o envio dos pedidos para os clientes.
    Pode integrar com serviços de terceiros para calcular o custo de envio, gerar etiquetas, e atualizar o status de envio.

### 3. Customer Service (Serviço de Clientes)

    Armazena e gerencia informações sobre os clientes.
    Pode fornecer funcionalidades como cadastro, autenticação, e histórico de pedidos.

### 4. Payment Service (Serviço de Pagamento)

  pode se conectar com gateways de pagamento (como Stripe, PayPal, etc.) para processar os pagamentos.

### 5. Notification Service (Serviço de Notificação)

    pode enviar notificações via e-mail, SMS ou outros canais (como notificações push) para alertar os usuários sobre o status do pedido.

### 6. Reporting Service (Serviço de Relatórios)

    Gera relatórios sobre o desempenho dos pedidos, vendas e análise de estoque.
    Pode usar ferramentas de BI ou gerar relatórios personalizados.

### 7. Auth Service (Serviço de Autenticação)

    Autenticação para gerenciar sessões, login, controle de acesso (JWT, OAuth2).
    Exemplo de endpoint: /auth/login, /auth/register

### 8. Analytics Service (Serviço de Análise)

    Coleta dados sobre o uso do sistema, comportamento do usuário e performance, fornecendo insights para otimizar os processos.
    Exemplo de endpoint: /analytics/overview

### 9. Audit Service (Serviço de Auditoria)

    Registra todas as ações críticas no sistema, como mudanças no status dos pedidos, alterações de preços, e autenticação de usuários.
    Exemplo de endpoint: /audit/logs


etc
