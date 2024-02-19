FROM public.ecr.aws/amazonlinux/amazonlinux:2023
COPY duck.sh /
RUN dnf install -y jq unzip tree less bind-utils \
  	&& dnf clean all \
  	&& rm -rf /var/cache/yum \
	&& curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" \
	&& unzip awscliv2.zip \
	&& rm awscliv2.zip \
	&& aws/install \
	&& rm -rf aws \
        && chmod +x /duck.sh
CMD ["./duck.sh"]
