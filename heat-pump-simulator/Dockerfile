FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ENV MODBUS_HOST=0.0.0.0
ENV MODBUS_PORT=5020
ENV HEATPUMP_PROFILE=idm

EXPOSE 5020

CMD ["python", "heatpump_sim_modbus.py"]
