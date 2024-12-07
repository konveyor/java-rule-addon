FROM golang:1.18 as builder

WORKDIR /windup-shim 
RUN git clone -b master --single-branch https://github.com/fabianvf/windup-rulesets-yaml.git /windup-shim
RUN go build -o windup-shim main.go

WORKDIR /analyzer-lsp
RUN git clone -b main --single-branch https://github.com/konveyor/analyzer-lsp.git /analyzer-lsp
RUN make build

FROM quay.io/konveyor/jdtls-server-base:testing

COPY --from=builder /analyzer-lsp/konveyor-analyzer /usr/bin/konveyor-analyzer
COPY --from=builder /analyzer-lsp/provider_container_settings.json /analyzer-lsp/provider_settings.json

RUN microdnf install git -y 
RUN git clone -b master --depth=1 --progress --verbose --single-branch https://github.com/windup/windup-rulesets.git /windup-rulesets

COPY --from=builder /windup-shim/windup-shim /usr/bin/windup-shim

WORKDIR /windup-shim

ENTRYPOINT ["windup-shim"]
