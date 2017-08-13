#include <stdio.h>
#include <stdlib.h>
#include <winsock2.h>
#include<string.h>
#include<math.h>
#include<time.h>
#include "utils.h"
#include <omp.h>
#include <opencv2\opencv.hpp>
#include <iostream>
#include <fstream>
const int DIR_LENGTH = 256;
#define BUF_SIZE 1024
#define DIR_NUM 10
#define MAXCLIENTS 10//实际最多只能9个用户同时登陆
#pragma comment (lib, "ws2_32.lib")  //加载 ws2_32.dll

float test(char filePath[]);
int stuNumToLabel(char stuNum[]);

using namespace std;
using namespace cv;

int CLIENT = 0;//定义客户数

DWORD WINAPI ProcessClientRequest(LPVOID lpParam)
{

	FILE *fp = NULL;
	bool isChangeFile = false;
	SOCKET* sServer = (SOCKET*)lpParam;

	printf("======SYSO:Start the process of client %d...\n\n", CLIENT);
	while (TRUE)
	{

		isChangeFile = false;
		char szBuffer[11] = { 0 };//缓冲区大小必须大于要接受的字节数11>10
		int s = recv(*sServer, szBuffer, 10, NULL);

		int key = stuNumToLabel(szBuffer);

		printf("key:%d\n", key);

		char filePath[19] = { "G://" };
		strcat(filePath, szBuffer);
		strcat(filePath, ".jpg");
		if (s>0) {
			isChangeFile = true;
		}
		if (isChangeFile == true)
		{

			fp = fopen(filePath, "wb+");  //以二进制方式打开（创建）文件
			if (fp == NULL) {
				printf("Cannot open file, press any key to exit!\n");
				exit(0);
			}
		}
		Sleep(1000);//延时1s，防止学号和图片的字节流发生粘包//数据的无边界性

		printf("======SYSO:Message form server:%s\n", szBuffer);
		char buffer[BUF_SIZE] = { 0 };  //文件缓冲区
		int nCount;

		int rs = 1;
		while (rs)
		{
			nCount = recv(*sServer, buffer, BUF_SIZE, 0);
			if (nCount < 0) {
				if (errno == EAGAIN) {
					break;
				}
				else {
					return 0;
				}
			}
			else if (nCount == 0) {
				//recv()返回值为0，表示一次性全部传输完成，自然关闭
				;
			}if (nCount != sizeof(buffer)) {
				fwrite(buffer, sizeof(char), nCount, fp);
				rs = 0;
				printf("======SYSO:File transfers succeed!\n");

				float result = test(filePath);

				if (key < 0) {
					char *msg = "StuNum does not match picture";
					int i = send(*sServer, msg, strlen(msg) + sizeof(char), NULL);
				}
				else if (abs(result - key)<0.1)
				{
					printf("**********%f\n", result);
					char *msg = "RESULT_OK\r\n";
					int i = send(*sServer, msg, strlen(msg) + sizeof(char), NULL);
				}
				else {
					char *msg = "RESULT_FLASE\r\n";
					int i = send(*sServer, msg, strlen(msg) + sizeof(char), NULL);
				}

			}
			else {
				fwrite(buffer, sizeof(char), nCount, fp);
				rs = 1;
			}
		}
		printf("======SYSO:Wait for another client to connect...\n");
		printf("****************************************************\n\n");
		fclose(fp);
		closesocket(*sServer);
		return 0;
	}
}


int main() {

	HANDLE threads[MAXCLIENTS];    //线程存放 数组
	int CountClient = 0;
	printf("======SYSO:Wait for connecting:\n\n");
	int ret, length;
	SOCKET servSock, sServer;
	WSADATA wsaData;
	//初始化 DLL
	ret = WSAStartup(MAKEWORD(2, 2), &wsaData);
	if (ret != 0)
	{
		printf("WSAstartup() failed!");
	}
	//创建套接字
	servSock = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
	if (servSock == INVALID_SOCKET)
	{
		WSACleanup();
		printf("Socket() failed!");
	}
	struct sockaddr_in saClient; //地址信息 //地址信息
	sockaddr_in sockAddr;
	memset(&sockAddr, 0, sizeof(sockAddr));  //每个字节都用0填充
	sockAddr.sin_family = PF_INET;  //使用IPv4地址
	sockAddr.sin_port = htons(8888);  //端口
									  //绑定套接字
	ret = bind(servSock, (SOCKADDR*)&sockAddr, sizeof(SOCKADDR));
	if (ret == SOCKET_ERROR)
	{
		printf("Bind() failed! code:%d\n", WSAGetLastError());
		closesocket(servSock);
		WSACleanup();
	}
	//进入监听状态

	ret = listen(servSock, 20);
	if (ret == SOCKET_ERROR)
	{
		printf("Listen() failed! code:%d\n", WSAGetLastError());
		closesocket(servSock);
	}

	while (TRUE)
	{
		//printf("Tips:Ctrl+c to quit!\n");
		length = sizeof(saClient);
		//接受客户端请求
		sServer = accept(servSock, (struct sockaddr *)&saClient, &length);

		if (sServer == INVALID_SOCKET)
		{
			printf("Accept() failed! code:%d\n", WSAGetLastError());
			closesocket(servSock);
			WSACleanup();
		}
		if (CountClient < MAXCLIENTS)                        //创建新线程
		{
			CLIENT++;
			threads[CountClient++] = CreateThread(NULL, 0, &ProcessClientRequest, &sServer, 0, NULL);
			printf("======STSO:Welcome  client %d\n", CLIENT);
		}
		else                                                //线程数超了 拒绝服务
		{
			char *msg = " Error Too many client Connecttion  !.\r\n";
			send(sServer, msg, strlen(msg) + sizeof(char), NULL);
			printf("======SYSO:REFUSED !.\n");
			closesocket(sServer);
		}

	}
	printf("Maximize clients occurred for %d.\r\n", MAXCLIENTS);
	WaitForMultipleObjects(MAXCLIENTS, threads, TRUE, INFINITE);
	closesocket(servSock);

	for (int i = 0; i<MAXCLIENTS; i++)
	{
		CloseHandle(threads[i]);
	}
	WSACleanup();
	getchar();
	system("pause");
	return 0;


}
//**********************************************************************
//**********************************************************************
//**************************对图片进行测试********************************

float test(char filePath[]) {

	vector<Mat> testImg;
	IplImage* img;
	IplImage* smallimg;

	IplImage *change;
	cv::Mat* bmtx;
	vector<cv::Mat> InImgs;

	const int train_num = 9;

	vector<int> NumFilters;
	NumFilters.push_back(8);
	NumFilters.push_back(8);
	vector<int> blockSize;
	blockSize.push_back(12);   //  height / 4
	blockSize.push_back(10);    //  width / 4

	PCANet pcaNet = {
		2,
		7,
		NumFilters,
		blockSize,
		0.5
	};

	//here
	CvSVM SVM;
	PCA_Train_Result* result = new PCA_Train_Result;//= PCANet_train(InImgs, &pcaNet, true);
	cv::Mat tmp1 = cv::Mat::zeros(8, 49, CV_64F);
	cv::Mat tmp2 = cv::Mat::zeros(8, 49, CV_64F);
	result->Filters.push_back(tmp1);
	result->Filters.push_back(tmp2);
	FileStorage fs("E:\\DaChuang\\model\\all_age_filters.xml", FileStorage::READ);
	if (!fs.isOpened())
	{
		printf("======SYSO:Can not read xml file!\n");

	}
	fs["filter1"] >> result->Filters[0];
	fs["filter2"] >> result->Filters[1];
	fs.release();
	SVM.load("E:\\DaChuang\\model\\all_age_svm.xml");



	int testNum = 1;
	CvSize sz;
	sz.width = 64;
	sz.height = 64;

	//char test[] = { "G:\\1_6.jpg" };
	img = cvLoadImage(filePath, CV_LOAD_IMAGE_GRAYSCALE);

	//加转
		smallimg = cvCreateImage(sz, img->depth, img->nChannels);
	cvResize(img, smallimg, CV_INTER_LINEAR);

	change = cvCreateImage(cvGetSize(smallimg), IPL_DEPTH_64F, smallimg->nChannels);
	cvConvertScale(smallimg, change, 1.0 / 255, 0);
	bmtx = new cv::Mat(change);
	testImg.push_back(*bmtx);


	Hashing_Result* hashing_r;
	PCA_Out_Result *out;

	out = new PCA_Out_Result;
	out->OutImgIdx.push_back(0);
	out->OutImg.push_back(testImg[0]);
	out = PCA_output(out->OutImg, out->OutImgIdx, pcaNet.PatchSize,
		pcaNet.NumFilters[0], result->Filters[0], 2);
	for (int j = 1; j<pcaNet.NumFilters[1]; j++)
		out->OutImgIdx.push_back(j);

	out = PCA_output(out->OutImg, out->OutImgIdx, pcaNet.PatchSize,
		pcaNet.NumFilters[1], result->Filters[1], 2);
	hashing_r = HashingHist(&pcaNet, out->OutImgIdx, out->OutImg);
	hashing_r->Features.convertTo(hashing_r->Features, CV_32F);
	float pred = SVM.predict(hashing_r->Features);
	printf("%f\n", pred);

	delete out;
	return pred;

}
int stuNumToLabel(char stuNum[]) {
	char key[8] = { 0 };
	for (int i = 2; i <= 9; i++) {
		key[i - 2] = stuNum[i];
	}
	//printf("%d\n",atoi(key));
	int keyValue = atoi(key);
	printf("%d\n", keyValue);
	switch (keyValue)
	{
	case 30414412:return 1; break;
	case 70214111:return 2; break;
	case 70214102:return 3; break;
	case 70214107:return 4; break;
	case 70214108:return 5; break;
	case 70214109:return 6; break;
	case 70214228:return 7; break;
	case 70214222:return 8; break;
	case 70214333:return 9; break;
	case 70214444:return 10; break;
	default:return -1;
		break;
	}
}
