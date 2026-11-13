FROM jenkins/jenkins:lts-jdk21

USER root

# Install prerequisites and Maven
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       maven ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

# Add Docker's official Debian repository
RUN install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg \
       -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
       > /etc/apt/sources.list.d/docker.list

# Install Docker CLI only
RUN apt-get update \
    && apt-get remove -y docker.io || true

RUN apt-get install -y --no-install-recommends docker-ce-cli \
    && rm -rf /var/lib/apt/lists/*

USER jenkins
