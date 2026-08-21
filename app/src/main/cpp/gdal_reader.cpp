#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "ogrsf_frmts.h"
#include "ogr_api.h"
#include "ogr_spatialref.h"

#define TAG "GDAL_NATIVE"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_gis_NativeGdalParser_readMapInfo(JNIEnv *env, jobject thiz, jstring path) {
    const char *pszPath = env->GetStringUTFChars(path, nullptr);
    LOGD("Opening MapInfo file: %s", pszPath);

    GDALAllRegister();
    OGRRegisterAll();

    // Use GDALOpenEx with vector flag
    GDALDataset *poDS = (GDALDataset *) GDALOpenEx(pszPath, GDAL_OF_VECTOR, NULL, NULL, NULL);
    if (poDS == nullptr) {
        LOGE("Failed to open dataset: %s", CPLGetLastErrorMsg());
        env->ReleaseStringUTFChars(path, pszPath);
        return nullptr;
    }

    OGRLayer *poLayer = poDS->GetLayer(0);
    if (poLayer == nullptr) {
        LOGE("Failed to get layer");
        GDALClose(poDS);
        env->ReleaseStringUTFChars(path, pszPath);
        return nullptr;
    }

    // Setup Coordinate Transformation (VN-2000 to WGS84)
    // In production, we'd read the CRS from the .TAB file.
    // Here we assume VN-2000 setup if needed, or WGS84 if available.
    OGRSpatialReference *poSRS = poLayer->GetSpatialRef();
    OGRSpatialReference oWGS84;
    oWGS84.importFromEPSG(4326);
    OGRCoordinateTransformation *poCT = nullptr;

    if (poSRS != nullptr) {
        poCT = OGRCreateCoordinateTransformation(poSRS, &oWGS84);
    }

    // Prepare ArrayList for results
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject featureList = env->NewObject(arrayListClass, arrayListInit);

    // Get NativeGdalParser class for callback
    jclass parserClass = env->FindClass("com/example/gis/NativeGdalParser");
    jmethodID createFeatureMid = env->GetStaticMethodID(parserClass, "createFeature",
        "(Ljava/lang/String;JI[D[D[Ljava/lang/String;[Ljava/lang/String;DDDD)Lcom/example/data/model/GisFeature;");

    poLayer->ResetReading();
    OGRFeature *poFeature;
    int featureCount = 0;

    while ((poFeature = poLayer->GetNextFeature()) != nullptr) {
        OGRGeometry *poGeometry = poFeature->GetGeometryRef();
        if (poGeometry == nullptr) {
            OGRFeature::DestroyFeature(poFeature);
            continue;
        }

        // Reproject to WGS84
        if (poCT != nullptr) {
            poGeometry->transform(poCT);
        }

        // Extract coordinates (simplified for Polygon/Line)
        std::vector<double> lats, lons;
        int shapeType = 0; // Point

        OGREnvelope oEnvelope;
        poGeometry->getEnvelope(&oEnvelope);

        if (wkbFlatten(poGeometry->getGeometryType()) == wkbPoint) {
            OGRPoint *p = (OGRPoint *) poGeometry;
            lats.push_back(p->getY());
            lons.push_back(p->getX());
            shapeType = 0;
        } else if (wkbFlatten(poGeometry->getGeometryType()) == wkbLineString) {
            OGRLineString *ls = (OGRLineString *) poGeometry;
            for (int i = 0; i < ls->getNumPoints(); i++) {
                lats.push_back(ls->getY(i));
                lons.push_back(ls->getX(i));
            }
            shapeType = 1;
        } else if (wkbFlatten(poGeometry->getGeometryType()) == wkbPolygon) {
            OGRPolygon *poly = (OGRPolygon *) poGeometry;
            OGRLinearRing *ring = poly->getExteriorRing();
            if (ring) {
                for (int i = 0; i < ring->getNumPoints(); i++) {
                    lats.push_back(ring->getY(i));
                    lons.push_back(ring->getX(i));
                }
            }
            shapeType = 2;
        }

        // Extract attributes
        int fieldCount = poFeature->GetFieldCount();
        std::vector<std::string> keys, values;
        for (int i = 0; i < fieldCount; i++) {
            keys.push_back(poFeature->GetFieldDefnRef(i)->GetNameRef());
            values.push_back(poFeature->GetFieldAsString(i));
        }

        // Convert to JNI types
        jstring jId = env->NewStringUTF(std::to_string(featureCount++).c_str());
        jdoubleArray jLats = env->NewDoubleArray(lats.size());
        env->SetDoubleArrayRegion(jLats, 0, lats.size(), lats.data());
        jdoubleArray jLons = env->NewDoubleArray(lons.size());
        env->SetDoubleArrayRegion(jLons, 0, lons.size(), lons.data());

        jobjectArray jKeys = env->NewObjectArray(keys.size(), env->FindClass("java/lang/String"), nullptr);
        jobjectArray jValues = env->NewObjectArray(values.size(), env->FindClass("java/lang/String"), nullptr);
        for(size_t i=0; i<keys.size(); ++i) {
            env->SetObjectArrayElement(jKeys, i, env->NewStringUTF(keys[i].c_str()));
            env->SetObjectArrayElement(jValues, i, env->NewStringUTF(values[i].c_str()));
        }

        // Call Kotlin helper
        jobject jFeature = env->CallStaticObjectMethod(parserClass, createFeatureMid,
            jId, (jlong)0, shapeType, jLats, jLons, jKeys, jValues,
            oEnvelope.MinY, oEnvelope.MaxY, oEnvelope.MinX, oEnvelope.MaxX);

        if (jFeature != nullptr) {
            env->CallBooleanMethod(featureList, arrayListAdd, jFeature);
        }

        // Cleanup local refs to avoid table overflow
        env->DeleteLocalRef(jId);
        env->DeleteLocalRef(jLats);
        env->DeleteLocalRef(jLons);
        env->DeleteLocalRef(jKeys);
        env->DeleteLocalRef(jValues);
        env->DeleteLocalRef(jFeature);

        OGRFeature::DestroyFeature(poFeature);
    }

    if (poCT) OCTDestroyCoordinateTransformation(poCT);
    GDALClose(poDS);
    env->ReleaseStringUTFChars(path, pszPath);

    return featureList;
}
