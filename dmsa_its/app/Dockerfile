FROM artifact-docker-base-image-local.digital.oocl.com/common/spark-operator/spark-oocl:v3.3.0-hadoop3-20221021

WORKDIR /log4j2
ADD src/main/resources/log4j2prd.properties .
ADD src/main/resources/log4j2dev.properties .

# Create app directory
WORKDIR /home/spark-jar
# Put the jar built by CMS into /home/spark-jar of the image
ADD target/dmsa_its-0.0.1.jar .
ENTRYPOINT ["/opt/entrypoint.sh"]