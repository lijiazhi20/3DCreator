FROM nvidia/cuda:12.4.1-devel-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.11 python3-pip python3.11-venv \
    libgl1 libglib2.0-0 libsm6 libxext6 libxrender-dev \
    ffmpeg git wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt

COPY . .

# Pre-download model weights to /weights at build or runtime
# RUN python3 scripts/download_weights.py

CMD ["python3", "worker.py"]
